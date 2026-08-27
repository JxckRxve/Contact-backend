"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const {publicProviderStatus, callProvider} = require("./ai/providers");
const {chooseProvider} = require("./ai/router");
const {CORE_SYSTEM, makePlan} = require("./ai/agent");

const PORT = Number(process.env.PORT || 8080);
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || "";
const ADMIN_CHAT_ID = String(process.env.TELEGRAM_ADMIN_CHAT_ID || "");
const DB_PATH = process.env.DB_PATH || path.join(__dirname, "data.json");
const CORS_ORIGIN = process.env.CORS_ORIGIN || "*";
const MESSAGE_LIMIT_PER_HOUR = Number(process.env.MESSAGE_LIMIT_PER_HOUR || 30);
const STARTING_COINS = 150;
const DAILY_COINS = 5;
const FEEDBACK_REWARD = 10;
const RESERVE_TAKE = 10;

const TASK_COSTS = Object.freeze({
  chat: 0,
  persona_chat: 0,
  deep_chat: 5,
  persona_refine: 5,
  plan: 5
});

const PERSONA_SCHEMA = {
  version:"0.5.0",
  kinds:[
    {id:"real_person",title:"Реальный человек",emoji:"👤",subtitle:"Тот, кого ты знаешь или помнишь."},
    {id:"character",title:"Персонаж",emoji:"🎭",subtitle:"Из фильма, игры, книги, аниме или сериала."},
    {id:"hybrid",title:"Гибрид",emoji:"🧬",subtitle:"Смешай несколько образов в одного."},
    {id:"original",title:"Оригинальный герой",emoji:"⚔️",subtitle:"Новая личность с нуля."},
    {id:"alter_ego",title:"Альтер-эго",emoji:"🪞",subtitle:"Другая версия тебя."},
    {id:"mentor",title:"Наставник",emoji:"🧠",subtitle:"Личность под конкретную задачу."}
  ],
  buildModes:[
    {id:"quick",title:"Быстро",hint:"5–7 вопросов",cost:0},
    {id:"guided",title:"В диалоге",hint:"CONTACT сам задаёт вопросы",cost:0},
    {id:"precise",title:"Точно",hint:"12–20 вопросов",cost:5},
    {id:"references",title:"По референсам",hint:"Начни с изображений",cost:0}
  ],
  copy:{
    headline:"Тебе не нужен хороший промт. Мне нужны твои ответы.",
    disclaimer:"PERSONA — AI-интерпретация на основе твоих ответов и материалов. Это не подтверждение личности и не сам человек."
  }
};

const state = loadState();
const rate = new Map();
let telegramOffset = 0;

function loadState() {
  try {
    const p = JSON.parse(fs.readFileSync(DB_PATH, "utf8"));
    return {
      conversations: p.conversations || {},
      messages: p.messages || [],
      feedback: p.feedback || [],
      personas: p.personas || {},
      wallets: p.wallets || {},
      coinLedger: p.coinLedger || [],
      memories: p.memories || {},
      aiLogs: p.aiLogs || [],
      reserve: Number(p.reserve || 0)
    };
  } catch {
    return {conversations:{},messages:[],feedback:[],personas:{},wallets:{},coinLedger:[],memories:{},aiLogs:[],reserve:0};
  }
}

function persist() {
  const tmp = DB_PATH + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(state, null, 2), "utf8");
  fs.renameSync(tmp, DB_PATH);
}

function json(res, status, body) {
  res.writeHead(status, {
    "Content-Type":"application/json; charset=utf-8",
    "Access-Control-Allow-Origin":CORS_ORIGIN,
    "Access-Control-Allow-Headers":"Content-Type",
    "Access-Control-Allow-Methods":"GET,POST,PATCH,DELETE,OPTIONS",
    "Cache-Control":"no-store",
    "X-Content-Type-Options":"nosniff"
  });
  res.end(JSON.stringify(body));
}

function html(res, status, body) {
  res.writeHead(status,{"Content-Type":"text/html; charset=utf-8","Cache-Control":"public, max-age=300","X-Content-Type-Options":"nosniff"});
  res.end(body);
}

async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 256000) throw new Error("payload_too_large");
  }
  return raw ? JSON.parse(raw) : {};
}

function clean(v, max=4000) { return String(v || "").replace(/\u0000/g, "").trim().slice(0,max); }
function validUuidLike(v) { return /^[0-9a-fA-F-]{30,50}$/.test(v); }
function identityShape(id, secret) { return validUuidLike(id) && String(secret || "").length >= 40; }
function validIdentity(id, secret) { const c = state.conversations[id]; return c && c.installSecret === secret; }

function utcDay(ts = Date.now()) { return new Date(ts).toISOString().slice(0,10); }

function ensureWallet(id) {
  if (!state.wallets[id]) {
    state.wallets[id] = {balance:STARTING_COINS,createdAt:Date.now(),lastDailyAt:0,lastReserveAt:0};
    state.coinLedger.push({id:crypto.randomUUID(),conversationId:id,delta:STARTING_COINS,reason:"welcome",createdAt:Date.now()});
  }
  return state.wallets[id];
}

function ensureConversation(id, secret) {
  if (!state.conversations[id]) {
    state.conversations[id] = {installSecret:secret,createdAt:Date.now(),sessionNumber:1,sessionStatus:"open"};
  }
  ensureWallet(id);
  if (!state.memories[id]) state.memories[id] = [];
  persist();
  return state.conversations[id];
}

function authOrCreate(id, secret) {
  if (!identityShape(id,secret)) return false;
  const existing = state.conversations[id];
  if (existing && existing.installSecret !== secret) return false;
  ensureConversation(id,secret);
  return true;
}

function changeCoins(id, delta, reason, meta={}) {
  const wallet = ensureWallet(id);
  const n = Math.trunc(Number(delta || 0));
  if (!Number.isFinite(n)) throw new Error("invalid_coin_delta");
  if (wallet.balance + n < 0) return false;
  wallet.balance += n;
  state.coinLedger.push({id:crypto.randomUUID(),conversationId:id,delta:n,reason,meta,createdAt:Date.now()});
  persist();
  return true;
}

function rateAllowed(key) {
  const now = Date.now();
  const recent = (rate.get(key) || []).filter(t => now-t < 3600000);
  if (recent.length >= MESSAGE_LIMIT_PER_HOUR) return false;
  recent.push(now); rate.set(key,recent); return true;
}

function crisisTag(text) {
  const t = String(text || "").toLowerCase();
  return ["хочу умереть","хочу убить себя","покончу с собой","покончить с собой","суицид","не хочу жить","себя убить","убью себя"].some(x => t.includes(x));
}

function addMessage(conversationId, channel, role, text) {
  const m = {id:crypto.randomUUID(),conversationId,channel,role,text,createdAt:Date.now()};
  state.messages.push(m); persist(); return m;
}

async function telegram(method, payload) {
  if (!BOT_TOKEN) return null;
  const r = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
  if (!r.ok) throw new Error(`telegram_${method}_${r.status}`);
  return r.json();
}

async function notifyAdmin(m) {
  if (!BOT_TOKEN || !ADMIN_CHAT_ID) return;
  const risk = crisisTag(m.text);
  const label = m.channel === "higher" ? "HIGHER" : "DEVELOPER";
  const text = `${risk ? "🚨 ВОЗМОЖНА СРОЧНАЯ СИТУАЦИЯ" : "🟣 CONTACT УСТАНОВЛЕН"}\nКанал: ${label}\nДиалог: ${m.conversationId}\n\n${m.text}\n\nОтвет:\n/reply ${m.conversationId} ${m.channel} ваш текст\n\nЗавершить:\n/close ${m.conversationId}`;
  await telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text});
}

async function handleTelegramMessage(m) {
  if (!m || !m.chat || String(m.chat.id) !== ADMIN_CHAT_ID) return;
  const text = String(m.text || "").trim();
  let match = text.match(/^\/reply\s+([0-9a-fA-F-]{30,50})\s+(developer|higher)\s+([\s\S]+)$/);
  if (match) {
    const [,id,channel,answerRaw] = match;
    if (!state.conversations[id]) return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:"Диалог не найден."});
    addMessage(id,channel,"human",clean(answerRaw));
    return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:`✅ CONTACT ${id} • ${channel}: ответ отправлен.`});
  }
  match = text.match(/^\/close\s+([0-9a-fA-F-]{30,50})$/);
  if (match) {
    const c = state.conversations[match[1]];
    if (!c) return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:"Диалог не найден."});
    c.sessionStatus = "closed"; persist();
    return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:`✨ CONTACT ${match[1]} завершён.`});
  }
  if (text === "/stats") {
    const byChannel = {};
    for (const x of state.messages) byChannel[x.channel] = (byChannel[x.channel] || 0) + 1;
    return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:`CONTACT 0.5\nДиалогов: ${Object.keys(state.conversations).length}\nСообщений: ${state.messages.length}\nPERSONA: ${Object.keys(state.personas).length}\nC-Reserve: ${state.reserve} C\nAI calls: ${state.aiLogs.length}\nDEVELOPER: ${byChannel.developer || 0}\nHIGHER: ${byChannel.higher || 0}`});
  }
}

async function pollTelegram() {
  if (!BOT_TOKEN || !ADMIN_CHAT_ID) return;
  while (true) {
    try {
      const r = await telegram("getUpdates",{offset:telegramOffset,timeout:25,allowed_updates:["message"]});
      for (const u of (r?.result || [])) { telegramOffset = u.update_id + 1; await handleTelegramMessage(u.message); }
    } catch(e) { console.error(e.message); await new Promise(r => setTimeout(r,3000)); }
  }
}

function personaSystemPrompt(p) {
  return `You are an AI PERSONA inside CONTACT, built from the user's supplied description and memories.\nNever claim to literally be the real person, their soul, consciousness, or an official licensed character.\nStay consistent, acknowledge uncertainty where profile data is missing, and reply naturally in the user's language.\n\nKIND: ${p.kind || "unspecified"}\nNAME: ${p.name}\nRELATIONSHIP: ${p.relationship}\nPERSONALITY: ${p.description}\nSPEECH STYLE: ${p.speech}\nMEMORIES: ${p.memories}\nNEVER SAY / BOUNDARIES: ${p.neverSay}`;
}

function memoryContext(id) {
  const items = (state.memories[id] || []).slice(-20);
  if (!items.length) return "";
  return `\n\nUSER-SAVED MEMORY:\n${items.map(x => `- ${x.text}`).join("\n")}`;
}

async function runAi({id,text,task="chat",quality="auto",personaId=null}) {
  const provider = chooseProvider({quality});
  if (!provider) return {ok:false,error:"ai_not_configured",providers:publicProviderStatus()};

  const cost = TASK_COSTS[task] ?? 0;
  const wallet = ensureWallet(id);
  if (wallet.balance < cost) return {ok:false,error:"not_enough_c",needed:cost-wallet.balance,cost,balance:wallet.balance};

  const persona = personaId ? state.personas[personaId] : null;
  if (personaId && (!persona || persona.conversationId !== id)) return {ok:false,error:"persona_not_found"};

  const system = persona ? personaSystemPrompt(persona) : CORE_SYSTEM + memoryContext(id);
  const result = await callProvider(provider,{messages:[{role:"system",content:system},{role:"user",content:text}],temperature:persona ? 0.82 : 0.65,maxTokens:1600});
  if (cost > 0) changeCoins(id,-cost,`ai:${task}`,{provider:result.provider,model:result.model});

  const log = {id:crypto.randomUUID(),conversationId:id,task,provider:result.provider,model:result.model,cost,createdAt:Date.now()};
  state.aiLogs.push(log); persist();
  return {ok:true,reply:result.text,provider:result.provider,model:result.model,cost,balance:ensureWallet(id).balance};
}

function sendLanding(res) {
  const p = path.join(__dirname,"public","index.html");
  if (fs.existsSync(p)) return html(res,200,fs.readFileSync(p,"utf8"));
  return html(res,200,"<!doctype html><meta charset='utf-8'><title>CONTACT</title><body style='background:#030106;color:#f8f5ff;font-family:sans-serif;padding:40px'><h1>CONTACT</h1><p>Establish contact.</p></body>");
}

const server = http.createServer(async (req,res) => {
  try {
    if (req.method === "OPTIONS") return json(res,200,{ok:true});
    const url = new URL(req.url,`http://${req.headers.host}`);

    if (req.method === "GET" && url.pathname === "/") return sendLanding(res);
    if (req.method === "GET" && url.pathname === "/health") return json(res,200,{ok:true,product:"CONTACT",version:"0.5.0",brain:"gateway-ready"});
    if (req.method === "GET" && url.pathname === "/api/ai/providers") return json(res,200,{ok:true,providers:publicProviderStatus()});
    if (req.method === "GET" && url.pathname === "/api/agent/capabilities") return json(res,200,{ok:true,version:"0.5.0",modules:{gateway:true,router:true,planner:true,memory:true,persona:true,wallet:true,telegramHuman:true,image:false,voice:false,video:false,threeD:false,browser:false,computer:false},providers:publicProviderStatus()});
    if (req.method === "GET" && url.pathname === "/api/persona/schema") return json(res,200,{ok:true,schema:PERSONA_SCHEMA});

    if (req.method === "GET" && url.pathname === "/api/wallet") {
      const id = clean(url.searchParams.get("conversationId"),60); const secret = clean(url.searchParams.get("installSecret"),180);
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      const w = ensureWallet(id);
      return json(res,200,{ok:true,balance:w.balance,reserve:state.reserve,startingCoins:STARTING_COINS,dailyCoins:DAILY_COINS});
    }

    if (req.method === "POST" && url.pathname === "/api/wallet/claim-daily") {
      const b = await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180);
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      const w=ensureWallet(id);
      if (utcDay(w.lastDailyAt) === utcDay()) return json(res,200,{ok:true,claimed:false,balance:w.balance,next:"tomorrow"});
      w.lastDailyAt=Date.now(); changeCoins(id,DAILY_COINS,"daily");
      return json(res,200,{ok:true,claimed:true,delta:DAILY_COINS,balance:ensureWallet(id).balance});
    }

    if (req.method === "POST" && url.pathname === "/api/wallet/reserve/give") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const amount=Math.max(1,Math.min(10000,Math.trunc(Number(b.amount||0))));
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      if (!changeCoins(id,-amount,"reserve_give")) return json(res,400,{ok:false,error:"not_enough_c",balance:ensureWallet(id).balance});
      state.reserve += amount; persist();
      return json(res,200,{ok:true,balance:ensureWallet(id).balance,reserve:state.reserve});
    }

    if (req.method === "POST" && url.pathname === "/api/wallet/reserve/take") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180);
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      const w=ensureWallet(id);
      if (utcDay(w.lastReserveAt) === utcDay()) return json(res,200,{ok:true,taken:false,balance:w.balance,reserve:state.reserve});
      const amount=Math.min(RESERVE_TAKE,state.reserve);
      if (amount <= 0) return json(res,200,{ok:true,taken:false,balance:w.balance,reserve:0});
      state.reserve -= amount; w.lastReserveAt=Date.now(); changeCoins(id,amount,"reserve_take"); persist();
      return json(res,200,{ok:true,taken:true,delta:amount,balance:ensureWallet(id).balance,reserve:state.reserve});
    }

    if (req.method === "POST" && url.pathname === "/api/message") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const text=clean(b.text,4000); const channel=["developer","higher"].includes(b.channel)?b.channel:"developer";
      if (!identityShape(id,secret) || !text) return json(res,400,{ok:false,error:"invalid_fields"});
      if (!rateAllowed(`${id}:${channel}`)) return json(res,429,{ok:false,error:"too_many_messages"});
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      const m=addMessage(id,channel,"user",text); notifyAdmin(m).catch(e=>console.error(e.message));
      return json(res,200,{ok:true,messageId:m.id,sessionStatus:"open"});
    }

    if (req.method === "GET" && url.pathname === "/api/conversation") {
      const id=clean(url.searchParams.get("conversationId"),60); const secret=clean(url.searchParams.get("installSecret"),180); const channel=["developer","higher"].includes(url.searchParams.get("channel"))?url.searchParams.get("channel"):"developer";
      if (!validIdentity(id,secret)) return json(res,403,{ok:false,messages:[]});
      const c=state.conversations[id];
      return json(res,200,{ok:true,messages:state.messages.filter(m=>m.conversationId===id&&m.channel===channel),sessionStatus:c.sessionStatus||"open",sessionNumber:c.sessionNumber||1,isFirstSession:(c.sessionNumber||1)===1});
    }

    if (req.method === "POST" && url.pathname === "/api/persona") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180);
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      const personaId=crypto.randomUUID();
      const p={id:personaId,conversationId:id,kind:clean(b.kind,40)||"real_person",buildMode:clean(b.buildMode,40)||"guided",name:clean(b.name,120),relationship:clean(b.relationship,500),description:clean(b.description,3000),speech:clean(b.speech,3000),memories:clean(b.memories,6000),neverSay:clean(b.neverSay,3000),visualProfile:b.visualProfile&&typeof b.visualProfile==="object"?b.visualProfile:{},core:{visual:0,character:35,voice:0,memory:25,relationship:20},createdAt:Date.now()};
      if (!p.name || !p.description) return json(res,400,{ok:false,error:"persona_incomplete"});
      state.personas[personaId]=p; persist();
      const {id:_,conversationId:__,createdAt:___,...profile}=p;
      return json(res,200,{ok:true,personaId,profile});
    }

    if (req.method === "POST" && url.pathname === "/api/persona/message") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const personaId=clean(b.personaId,60); const text=clean(b.text,4000);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      const result=await runAi({id,text,task:"persona_chat",quality:clean(b.quality,20)||"auto",personaId});
      return json(res,result.ok?200:(result.error==="ai_not_configured"?503:400),result);
    }

    if (req.method === "POST" && url.pathname === "/api/ai/chat") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const text=clean(b.text,12000); const task=clean(b.task,40)||"chat"; const quality=clean(b.quality,20)||"auto"; const personaId=clean(b.personaId,60)||null;
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      if (!text) return json(res,400,{ok:false,error:"empty_text"});
      if (!rateAllowed(`${id}:ai`)) return json(res,429,{ok:false,error:"too_many_messages"});
      const result=await runAi({id,text,task,quality,personaId});
      return json(res,result.ok?200:(result.error==="ai_not_configured"?503:400),result);
    }

    if (req.method === "POST" && url.pathname === "/api/agent/plan") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const goal=clean(b.goal,12000); const context=clean(b.context,12000); const quality=clean(b.quality,20)||"auto";
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      if (!goal) return json(res,400,{ok:false,error:"empty_goal"});
      const cost=TASK_COSTS.plan;
      if (ensureWallet(id).balance < cost) return json(res,400,{ok:false,error:"not_enough_c",needed:cost-ensureWallet(id).balance,balance:ensureWallet(id).balance});
      const result=await makePlan(goal,context,quality);
      if (!result.ok) return json(res,503,result);
      changeCoins(id,-cost,"agent:plan",{provider:result.provider,model:result.model});
      state.aiLogs.push({id:crypto.randomUUID(),conversationId:id,task:"plan",provider:result.provider,model:result.model,cost,createdAt:Date.now()}); persist();
      return json(res,200,{...result,cost,balance:ensureWallet(id).balance,status:"plan_only",note:"Tool execution layer is intentionally not enabled yet."});
    }

    if (req.method === "GET" && url.pathname === "/api/memory") {
      const id=clean(url.searchParams.get("conversationId"),60); const secret=clean(url.searchParams.get("installSecret"),180);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      return json(res,200,{ok:true,items:state.memories[id]||[]});
    }

    if (req.method === "POST" && url.pathname === "/api/memory") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180); const text=clean(b.text,3000); const scope=clean(b.scope,40)||"user";
      if (!authOrCreate(id,secret)) return json(res,403,{ok:false});
      if (!text) return json(res,400,{ok:false,error:"empty_memory"});
      const item={id:crypto.randomUUID(),text,scope,createdAt:Date.now()}; state.memories[id].push(item); persist();
      return json(res,200,{ok:true,item});
    }

    if (req.method === "POST" && url.pathname === "/api/feedback") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      const alreadyRewarded=state.feedback.some(x=>x.conversationId===id&&x.rewarded);
      const helped=Boolean(b.helped); const item={conversationId:id,helped,createdAt:Date.now(),rewarded:false};
      if (helped&&!alreadyRewarded) { changeCoins(id,FEEDBACK_REWARD,"feedback"); item.rewarded=true; }
      state.feedback.push(item); persist();
      return json(res,200,{ok:true,reward:item.rewarded?FEEDBACK_REWARD:0,balance:ensureWallet(id).balance});
    }

    if (req.method === "POST" && url.pathname === "/api/delete") {
      const b=await readJson(req); const id=clean(b.conversationId,60); const secret=clean(b.installSecret,180);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      state.messages=state.messages.filter(m=>m.conversationId!==id);
      state.feedback=state.feedback.filter(x=>x.conversationId!==id);
      state.coinLedger=state.coinLedger.filter(x=>x.conversationId!==id);
      state.aiLogs=state.aiLogs.filter(x=>x.conversationId!==id);
      for (const [pid,p] of Object.entries(state.personas)) if (p.conversationId===id) delete state.personas[pid];
      delete state.memories[id]; delete state.wallets[id]; delete state.conversations[id]; persist();
      return json(res,200,{ok:true});
    }

    return json(res,404,{ok:false,error:"not_found"});
  } catch(e) {
    console.error(e);
    return json(res,500,{ok:false,error:"server_error",detail:process.env.NODE_ENV==="development"?e.message:undefined});
  }
});

server.listen(PORT,()=>console.log(`CONTACT backend 0.5: http://0.0.0.0:${PORT}`));
pollTelegram();

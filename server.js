"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 8080);
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || "";
const ADMIN_CHAT_ID = String(process.env.TELEGRAM_ADMIN_CHAT_ID || "");
const DB_PATH = process.env.DB_PATH || path.join(__dirname, "data.json");
const CORS_ORIGIN = process.env.CORS_ORIGIN || "*";
const MESSAGE_LIMIT_PER_HOUR = Number(process.env.MESSAGE_LIMIT_PER_HOUR || 30);

// Optional OpenAI-compatible provider for PERSONA.
// Example shape: https://provider.example/v1/chat/completions
const LLM_ENDPOINT = process.env.LLM_ENDPOINT || "";
const LLM_API_KEY = process.env.LLM_API_KEY || "";
const LLM_MODEL = process.env.LLM_MODEL || "";

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
      personas: p.personas || {}
    };
  } catch {
    return { conversations: {}, messages: [], feedback: [], personas: {} };
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
    "Access-Control-Allow-Origin": CORS_ORIGIN,
    "Access-Control-Allow-Headers":"Content-Type",
    "Access-Control-Allow-Methods":"GET,POST,OPTIONS",
    "Cache-Control":"no-store",
    "X-Content-Type-Options":"nosniff"
  });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 128000) throw new Error("payload_too_large");
  }
  return raw ? JSON.parse(raw) : {};
}

function clean(v, max=4000) {
  return String(v || "").replace(/\u0000/g, "").trim().slice(0,max);
}

function validUuidLike(v) {
  return /^[0-9a-fA-F-]{30,50}$/.test(v);
}

function validIdentity(id, secret) {
  const c = state.conversations[id];
  return c && c.installSecret === secret;
}

function ensureConversation(id, secret) {
  if (!state.conversations[id]) {
    state.conversations[id] = {
      installSecret: secret,
      createdAt: Date.now(),
      sessionNumber: 1,
      sessionStatus: "open"
    };
    persist();
  }
  return state.conversations[id];
}

function rateAllowed(key) {
  const now = Date.now();
  const win = 3600000;
  const recent = (rate.get(key) || []).filter(t => now-t < win);
  if (recent.length >= MESSAGE_LIMIT_PER_HOUR) return false;
  recent.push(now);
  rate.set(key, recent);
  return true;
}

function crisisTag(text) {
  const t = text.toLowerCase();
  return [
    "хочу умереть","хочу убить себя","покончу с собой","покончить с собой",
    "суицид","не хочу жить","себя убить","убью себя"
  ].some(x => t.includes(x));
}

function addMessage(conversationId, channel, role, text) {
  const m = {
    id: crypto.randomUUID(),
    conversationId,
    channel,
    role,
    text,
    createdAt: Date.now()
  };
  state.messages.push(m);
  persist();
  return m;
}

async function telegram(method, payload) {
  if (!BOT_TOKEN) return null;
  const r = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify(payload)
  });
  if (!r.ok) throw new Error(`telegram_${method}_${r.status}`);
  return r.json();
}

async function notifyAdmin(m) {
  if (!BOT_TOKEN || !ADMIN_CHAT_ID) return;
  const risk = crisisTag(m.text);
  const label = m.channel === "higher" ? "HIGHER" : "DEVELOPER";
  const text =
`${risk ? "🚨 ВОЗМОЖНА СРОЧНАЯ СИТУАЦИЯ" : "🟣 CONTACT УСТАНОВЛЕН"}
Канал: ${label}
Диалог: ${m.conversationId}

${m.text}

Ответ:
 /reply ${m.conversationId} ${m.channel} ваш текст

Завершить:
 /close ${m.conversationId}`;
  await telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text});
}

async function handleTelegramMessage(m) {
  if (!m || !m.chat || String(m.chat.id) !== ADMIN_CHAT_ID) return;
  const text = String(m.text || "").trim();

  let match = text.match(/^\/reply\s+([0-9a-fA-F-]{30,50})\s+(developer|higher)\s+([\s\S]+)$/);
  if (match) {
    const [, id, channel, answerRaw] = match;
    if (!state.conversations[id]) {
      return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:"Диалог не найден."});
    }
    addMessage(id, channel, "human", clean(answerRaw));
    return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:`✅ CONTACT ${id} • ${channel}: ответ отправлен.`});
  }

  match = text.match(/^\/close\s+([0-9a-fA-F-]{30,50})$/);
  if (match) {
    const c = state.conversations[match[1]];
    if (!c) return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:"Диалог не найден."});
    c.sessionStatus = "closed";
    persist();
    return telegram("sendMessage",{chat_id:ADMIN_CHAT_ID,text:`✨ CONTACT ${match[1]} завершён.`});
  }

  if (text === "/stats") {
    const byChannel = {};
    for (const m of state.messages) byChannel[m.channel] = (byChannel[m.channel] || 0) + 1;
    return telegram("sendMessage",{
      chat_id:ADMIN_CHAT_ID,
      text:`CONTACT 0.3\nДиалогов: ${Object.keys(state.conversations).length}\nСообщений: ${state.messages.length}\nPERSONA: ${Object.keys(state.personas).length}\nDEVELOPER: ${byChannel.developer || 0}\nHIGHER: ${byChannel.higher || 0}`
    });
  }
}

async function pollTelegram() {
  if (!BOT_TOKEN || !ADMIN_CHAT_ID) return;
  while (true) {
    try {
      const r = await telegram("getUpdates",{offset:telegramOffset,timeout:25,allowed_updates:["message"]});
      for (const u of (r?.result || [])) {
        telegramOffset = u.update_id + 1;
        await handleTelegramMessage(u.message);
      }
    } catch(e) {
      console.error(e.message);
      await new Promise(r => setTimeout(r,3000));
    }
  }
}

function personaSystemPrompt(p) {
  return `You are simulating an AI persona based only on the user's supplied memories.
Never claim to literally be the real person, their soul, consciousness, or a supernatural connection.
Stay consistent with the profile and gently acknowledge uncertainty when the profile lacks information.

NAME: ${p.name}
RELATIONSHIP: ${p.relationship}
PERSONALITY: ${p.description}
SPEECH STYLE: ${p.speech}
MEMORIES: ${p.memories}
NEVER SAY / BOUNDARIES: ${p.neverSay}

Reply naturally in the user's language.`;
}

async function personaReply(persona, text) {
  if (!LLM_ENDPOINT || !LLM_API_KEY || !LLM_MODEL) {
    return {
      ok:false,
      error:"llm_not_configured",
      reply:"PERSONA создана, но AI-провайдер ещё не подключён. Профиль уже сохранён."
    };
  }

  const r = await fetch(LLM_ENDPOINT, {
    method:"POST",
    headers:{
      "Content-Type":"application/json",
      "Authorization":`Bearer ${LLM_API_KEY}`
    },
    body:JSON.stringify({
      model:LLM_MODEL,
      messages:[
        {role:"system",content:personaSystemPrompt(persona)},
        {role:"user",content:text}
      ],
      temperature:0.8
    })
  });
  if (!r.ok) throw new Error(`llm_${r.status}`);
  const data = await r.json();
  const reply = data?.choices?.[0]?.message?.content || "";
  return {ok:true,reply};
}

const server = http.createServer(async (req,res) => {
  try {
    if (req.method === "OPTIONS") return json(res,200,{ok:true});
    const url = new URL(req.url, `http://${req.headers.host}`);

    if (req.method === "GET" && url.pathname === "/") {
      const landingPath = path.join(__dirname, "public", "index.html");
      const body = fs.readFileSync(landingPath, "utf8");
      res.writeHead(200, {
        "Content-Type":"text/html; charset=utf-8",
        "Cache-Control":"public, max-age=300",
        "X-Content-Type-Options":"nosniff"
      });
      return res.end(body);
    }

    if (req.method === "GET" && url.pathname === "/health") {
      return json(res,200,{ok:true,product:"CONTACT",version:"0.4.0"});
    }

    if (req.method === "POST" && url.pathname === "/api/message") {
      const b = await readJson(req);
      const id = clean(b.conversationId,60);
      const secret = clean(b.installSecret,180);
      const text = clean(b.text,4000);
      const channel = ["developer","higher"].includes(b.channel) ? b.channel : "developer";
      if (!validUuidLike(id) || secret.length < 40 || !text) return json(res,400,{ok:false,error:"invalid_fields"});
      if (!rateAllowed(`${id}:${channel}`)) return json(res,429,{ok:false,error:"too_many_messages"});

      const existing = state.conversations[id];
      if (existing && existing.installSecret !== secret) return json(res,403,{ok:false});
      ensureConversation(id,secret);

      const m = addMessage(id,channel,"user",text);
      notifyAdmin(m).catch(e => console.error(e.message));
      return json(res,200,{ok:true,messageId:m.id,sessionStatus:"open"});
    }

    if (req.method === "GET" && url.pathname === "/api/conversation") {
      const id = clean(url.searchParams.get("conversationId"),60);
      const secret = clean(url.searchParams.get("installSecret"),180);
      const channel = ["developer","higher"].includes(url.searchParams.get("channel")) ? url.searchParams.get("channel") : "developer";
      if (!validIdentity(id,secret)) return json(res,403,{ok:false,messages:[]});
      const messages = state.messages.filter(m => m.conversationId===id && m.channel===channel);
      const c = state.conversations[id];
      return json(res,200,{
        ok:true,messages,
        sessionStatus:c.sessionStatus || "open",
        sessionNumber:c.sessionNumber || 1,
        isFirstSession:(c.sessionNumber || 1) === 1
      });
    }

    if (req.method === "POST" && url.pathname === "/api/persona") {
      const b = await readJson(req);
      const id = clean(b.conversationId,60);
      const secret = clean(b.installSecret,180);
      if (!validUuidLike(id) || secret.length < 40) return json(res,400,{ok:false});
      const existing = state.conversations[id];
      if (existing && existing.installSecret !== secret) return json(res,403,{ok:false});
      ensureConversation(id,secret);

      const personaId = crypto.randomUUID();
      const p = {
        id:personaId,
        conversationId:id,
        name:clean(b.name,120),
        relationship:clean(b.relationship,500),
        description:clean(b.description,3000),
        speech:clean(b.speech,3000),
        memories:clean(b.memories,6000),
        neverSay:clean(b.neverSay,3000),
        createdAt:Date.now()
      };
      if (!p.name || !p.description) return json(res,400,{ok:false,error:"persona_incomplete"});
      state.personas[personaId] = p;
      persist();
      const {id:_,conversationId:__,createdAt:___,...profile} = p;
      return json(res,200,{ok:true,personaId,profile});
    }

    if (req.method === "POST" && url.pathname === "/api/persona/message") {
      const b = await readJson(req);
      const id = clean(b.conversationId,60);
      const secret = clean(b.installSecret,180);
      const personaId = clean(b.personaId,60);
      const text = clean(b.text,4000);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      const persona = state.personas[personaId];
      if (!persona || persona.conversationId !== id) return json(res,404,{ok:false,error:"persona_not_found"});
      const result = await personaReply(persona,text);
      return json(res,200,result);
    }

    if (req.method === "POST" && url.pathname === "/api/feedback") {
      const b = await readJson(req);
      if (!validIdentity(clean(b.conversationId,60), clean(b.installSecret,180))) return json(res,403,{ok:false});
      state.feedback.push({conversationId:b.conversationId,helped:Boolean(b.helped),createdAt:Date.now()});
      persist();
      return json(res,200,{ok:true});
    }

    if (req.method === "POST" && url.pathname === "/api/delete") {
      const b = await readJson(req);
      const id = clean(b.conversationId,60);
      const secret = clean(b.installSecret,180);
      if (!validIdentity(id,secret)) return json(res,403,{ok:false});
      state.messages = state.messages.filter(m => m.conversationId !== id);
      for (const [pid,p] of Object.entries(state.personas)) {
        if (p.conversationId === id) delete state.personas[pid];
      }
      delete state.conversations[id];
      persist();
      return json(res,200,{ok:true});
    }

    return json(res,404,{ok:false,error:"not_found"});
  } catch(e) {
    console.error(e);
    return json(res,500,{ok:false,error:"server_error"});
  }
});

server.listen(PORT,()=>console.log(`CONTACT backend 0.4: http://0.0.0.0:${PORT}`));
pollTelegram();

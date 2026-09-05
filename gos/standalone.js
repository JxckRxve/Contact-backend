"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const {chooseProvider} = require("../ai/router");
const {callProvider, publicProviderStatus} = require("../ai/providers");

const VERSION = "0.1.1";
const BASE_SPACES = [
  ["HOME","HOME","CORE"],["LIFE","LIFE","CORE"],["MONEY","MONEY","CORE"],["WORK","WORK","CORE"],
  ["CREATIVE","CREATIVE","CORE"],["RESEARCH","RESEARCH","CORE"],["GAME","GAME","CORE"],["ANALYSIS","ANALYSIS","CORE"],
  ["INTEL","INTEL","LAB"],["OPEN","OPEN","CORE"],["REALITY","REALITY","LAB"],["EVOLUTION","EVOLUTION","LAB"],
  ["SIMULATIONS","SIMULATIONS","LAB"],["MULTIVERSE","MULTIVERSE","LAB"]
];
const MEMORY_TYPES = Object.freeze({
  active:["working","episodic","personal","agent","space"],
  placeholder:["semantic","procedural","global","lineage"]
});
const MEMORY_TYPE_WEIGHT = Object.freeze({working:8,agent:6,personal:5,space:5,episodic:2});


function clean(v,max=4000){return String(v||"").replace(/\u0000/g,"").trim().slice(0,max);}
function validUuidLike(v){return /^[0-9a-fA-F-]{30,50}$/.test(v);}
function identityShape(id,secret){return validUuidLike(id)&&String(secret||"").length>=40;}
async function readJson(req){let raw="";for await(const chunk of req){raw+=chunk;if(raw.length>256000)throw new Error("payload_too_large");}return raw?JSON.parse(raw):{};}
function json(res,status,body){res.writeHead(status,{"Content-Type":"application/json; charset=utf-8","Access-Control-Allow-Origin":process.env.CORS_ORIGIN||"*","Access-Control-Allow-Headers":"Content-Type","Access-Control-Allow-Methods":"GET,POST,PATCH,DELETE,OPTIONS","Cache-Control":"no-store","X-Content-Type-Options":"nosniff"});res.end(JSON.stringify(body));}

function createStore(){
  const dbPath=process.env.GOS_DB_PATH||path.join(__dirname,"..","gos-data.json");
  let state;
  try{state=JSON.parse(fs.readFileSync(dbPath,"utf8"));}catch{state={users:{},spaces:{},personas:{},tasks:{},memories:[],experienceEvents:[],achievements:[],permissions:[],fitnessRecords:[]};}
  for(const [k,v] of Object.entries({users:{},spaces:{},personas:{},tasks:{},memories:[],experienceEvents:[],achievements:[],permissions:[],fitnessRecords:[]}))if(state[k]==null)state[k]=v;
  function persist(){const tmp=dbPath+".tmp";fs.writeFileSync(tmp,JSON.stringify(state,null,2),"utf8");fs.renameSync(tmp,dbPath);}
  return{state,persist,dbPath};
}

function createStandaloneGos(){
  const {state,persist}=createStore();
  const rate=new Map();
  const now=()=>Date.now();
  function rateAllowed(key){const t=now();const recent=(rate.get(key)||[]).filter(x=>t-x<3600000);if(recent.length>=Number(process.env.MESSAGE_LIMIT_PER_HOUR||30))return false;recent.push(t);rate.set(key,recent);return true;}
  function authOrCreate(id,secret){if(!identityShape(id,secret))return false;const u=state.users[id];if(u&&u.installSecret!==secret)return false;if(!u)state.users[id]={id,installSecret:secret,authType:"install",createdAt:now(),updatedAt:now()};ensureSpaces(id);persist();return true;}
  function ensureSpaces(id){let changed=false;for(const [key,name,status] of BASE_SPACES){if(!Object.values(state.spaces).some(s=>s.userId===id&&s.key===key&&!s.archivedAt)){const sid=crypto.randomUUID();state.spaces[sid]={id:sid,userId:id,key,name,status,isSystem:true,createdAt:now(),updatedAt:now()};changed=true;}}if(changed)persist();}
  function homeSpace(id){ensureSpaces(id);return Object.values(state.spaces).find(s=>s.userId===id&&s.key==="HOME"&&!s.archivedAt);}
  function ownedSpace(id,sid){const s=state.spaces[sid];return s&&s.userId===id&&!s.archivedAt?s:null;}
  function ownedPersona(id,pid){const p=state.personas[pid];return p&&p.userId===id&&!p.archivedAt?p:null;}
  function addPermission(id,pid,action,effect="allow"){const item={id:crypto.randomUUID(),userId:id,personaId:pid,action,effect,createdAt:now()};state.permissions.push(item);return item;}
  function allows(id,pid,action){const xs=state.permissions.filter(x=>x.userId===id&&x.personaId===pid&&x.action===action);return !xs.some(x=>x.effect==="deny");}
  function tokenizeMemory(v){return [...new Set(clean(v,12000).toLowerCase().split(/[^\p{L}\p{N}_-]+/u).filter(x=>x.length>=3))].slice(0,100);}
  function memoryAlive(m){return !m.expiresAt||Number(m.expiresAt)>now();}
  function memoryCompatible(m,pid,sid){if(m.personaId&&m.personaId!==pid)return false;if(m.spaceId&&m.spaceId!==sid)return false;if(m.type==="agent"&&!pid)return false;if(m.type==="space"&&!sid)return false;return true;}
  function rankMemories(id,pid,sid,query,limit=12){
    const queryWords=new Set(tokenizeMemory(query));
    return state.memories.filter(m=>m.userId===id&&memoryAlive(m)&&memoryCompatible(m,pid,sid)).map(m=>{
      let score=Number(MEMORY_TYPE_WEIGHT[m.type]||1),reasons=[`type:${m.type||"unknown"}`];
      if(m.personaId&&m.personaId===pid){score+=12;reasons.push("persona_scope");}
      if(m.spaceId&&m.spaceId===sid){score+=8;reasons.push("space_scope");}
      let overlap=0;for(const w of tokenizeMemory(m.text))if(queryWords.has(w))overlap++;if(overlap){score+=overlap*3;reasons.push(`query_overlap:${overlap}`);}
      const ageHours=Math.max(0,(now()-Number(m.createdAt||0))/3600000);const recency=Math.max(0,4-ageHours/24);score+=recency;if(recency>0)reasons.push("recent");
      return{memory:m,score:Number(score.toFixed(3)),reasons};
    }).sort((a,b)=>b.score-a.score||Number(b.memory.createdAt||0)-Number(a.memory.createdAt||0)).slice(0,Math.max(1,Math.min(50,Number(limit)||12)));
  }
  function relevantMemories(id,pid,sid,query,limit=12){return rankMemories(id,pid,sid,query,limit).map(x=>x.memory);}
  function personaPrompt(p,input){const space=state.spaces[p.spaceId];const mem=relevantMemories(p.userId,p.id,p.spaceId,input).map(x=>`- [${x.type}] ${x.text}`).join("\n")||"No relevant saved memory.";return `You are an AI Persona inside G-OS. Work as a specialist, not as a generic assistant. Never pretend an external action happened unless it actually did. Reply in the user's language.\n\nSPACE: ${space?.name||"HOME"}\nNAME: ${p.name}\nROLE: ${p.role}\nPERSONALITY: ${p.personality}\nTOOLS: ${(p.tools||[]).join(", ")||"none"}\nGENOME: ${JSON.stringify(p.genome)}\n\nRELEVANT MEMORY:\n${mem}`;}
  async function callPersona(p,input,quality){const provider=chooseProvider({quality});if(!provider)return{ok:false,error:"ai_not_configured",providers:publicProviderStatus()};try{const r=await callProvider(provider,{messages:[{role:"system",content:personaPrompt(p,input)},{role:"user",content:input}],temperature:0.7,maxTokens:1600});return{ok:true,reply:r.text,provider:r.provider,model:r.model,cost:0};}catch(e){return{ok:false,error:e.code||"ai_provider_error"};}}
  function createPersona(id,b){const space=ownedSpace(id,clean(b.spaceId,60))||homeSpace(id);if(!space)return{ok:false,error:"space_not_found"};const pid=crypto.randomUUID(),role=clean(b.role,120)||"assistant",personality=clean(b.personality,3000)||"Natural, useful, consistent.",tools=Array.isArray(b.tools)?b.tools.map(x=>clean(x,80)).filter(Boolean).slice(0,30):[];const p={id:pid,userId:id,spaceId:space.id,name:clean(b.name,120)||role,role,personality,tools,xp:0,level:1,status:"active",genome:{version:"0.1",role,prompt:clean(b.prompt,4000),planning:clean(b.planning,80)||"direct",memoryStrategy:"scoped_relevant_recent",model:clean(b.model,80)||"auto",tools:[...tools],communicationStyle:clean(b.communicationStyle,120)||"natural",parentPersonaId:null,generation:0,mutations:[]},createdAt:now(),updatedAt:now()};state.personas[pid]=p;addPermission(id,pid,"model.call");addPermission(id,pid,"memory.write");state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:pid,type:"persona_created",data:{name:p.name,role:p.role},createdAt:now()});persist();return{ok:true,persona:p};}
  function updatePersona(id,pid,b){
    const p=ownedPersona(id,pid);if(!p)return{ok:false,error:"persona_not_found",status:404};
    const changed=[];
    if(Object.prototype.hasOwnProperty.call(b,"spaceId")){const target=ownedSpace(id,clean(b.spaceId,60));if(!target)return{ok:false,error:"space_not_found",status:404};if(p.spaceId!==target.id){p.spaceId=target.id;changed.push("spaceId");}}
    for(const [field,max] of [["name",120],["role",120],["personality",3000]])if(Object.prototype.hasOwnProperty.call(b,field)){const value=clean(b[field],max);if(!value)return{ok:false,error:`${field}_required`,status:400};if(p[field]!==value){p[field]=value;changed.push(field);}}
    if(Object.prototype.hasOwnProperty.call(b,"tools")){if(!Array.isArray(b.tools))return{ok:false,error:"invalid_tools",status:400};p.tools=b.tools.map(x=>clean(x,80)).filter(Boolean).slice(0,30);changed.push("tools");}
    p.genome=p.genome&&typeof p.genome==="object"?p.genome:{};
    if(Object.prototype.hasOwnProperty.call(b,"prompt")){p.genome.prompt=clean(b.prompt,4000);changed.push("genome.prompt");}
    if(Object.prototype.hasOwnProperty.call(b,"planning")){p.genome.planning=clean(b.planning,80)||"direct";changed.push("genome.planning");}
    if(Object.prototype.hasOwnProperty.call(b,"model")){p.genome.model=clean(b.model,80)||"auto";changed.push("genome.model");}
    if(Object.prototype.hasOwnProperty.call(b,"communicationStyle")){p.genome.communicationStyle=clean(b.communicationStyle,120)||"natural";changed.push("genome.communicationStyle");}
    p.genome.role=p.role;p.genome.tools=[...(p.tools||[])];p.updatedAt=now();
    state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:p.spaceId,personaId:p.id,type:"persona_updated",data:{changed},createdAt:p.updatedAt});persist();return{ok:true,persona:p};
  }
  function clonePersona(id,pid,b){
    const source=ownedPersona(id,pid);if(!source)return{ok:false,error:"persona_not_found",status:404};
    const space=Object.prototype.hasOwnProperty.call(b,"spaceId")?ownedSpace(id,clean(b.spaceId,60)):ownedSpace(id,source.spaceId);if(!space)return{ok:false,error:"space_not_found",status:404};
    const newId=crypto.randomUUID(),clone=JSON.parse(JSON.stringify(source));clone.id=newId;clone.userId=id;clone.spaceId=space.id;clone.name=clean(b.name,120)||`${source.name} Copy`;clone.xp=0;clone.level=1;clone.status="active";clone.cloneOfPersonaId=source.id;clone.createdAt=now();clone.updatedAt=clone.createdAt;delete clone.archivedAt;
    state.personas[newId]=clone;
    const sourcePerms=state.permissions.filter(x=>x.userId===id&&x.personaId===source.id);
    if(sourcePerms.length){for(const x of sourcePerms)addPermission(id,newId,x.action,x.effect);}else{addPermission(id,newId,"model.call");addPermission(id,newId,"memory.write");}
    state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:clone.spaceId,personaId:newId,type:"persona_cloned",data:{sourcePersonaId:source.id,name:clone.name},createdAt:clone.createdAt});persist();return{ok:true,persona:clone};
  }
  function archivePersona(id,pid){
    const p=ownedPersona(id,pid);if(!p)return{ok:false,error:"persona_not_found",status:404};const ts=now();p.status="archived";p.archivedAt=ts;p.updatedAt=ts;state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:p.spaceId,personaId:p.id,type:"persona_archived",data:{name:p.name},createdAt:ts});persist();return{ok:true,persona:p};
  }
  function memorySchema(){return{version:"0.1",active:MEMORY_TYPES.active,placeholder:MEMORY_TYPES.placeholder,workingDefaultTtlMinutes:1440};}
  function createMemory(id,b){
    const text=clean(b.text,12000),type=clean(b.type,40).toLowerCase()||"episodic";if(!text)return{ok:false,error:"memory_text_required",status:400};
    if(!MEMORY_TYPES.active.includes(type))return{ok:false,error:MEMORY_TYPES.placeholder.includes(type)?"memory_type_placeholder":"invalid_memory_type",status:400};
    let spaceId=clean(b.spaceId,60)||null,personaId=clean(b.personaId,60)||null,taskId=clean(b.taskId,60)||null,scope="user";
    let space=spaceId?ownedSpace(id,spaceId):null,persona=personaId?ownedPersona(id,personaId):null;
    if(spaceId&&!space)return{ok:false,error:"space_not_found",status:404};if(personaId&&!persona)return{ok:false,error:"persona_not_found",status:404};
    if(persona&&spaceId&&persona.spaceId!==spaceId)return{ok:false,error:"persona_space_mismatch",status:400};
    if(type==="personal"){spaceId=null;personaId=null;scope="personal";}
    else if(type==="agent"){if(!persona)return{ok:false,error:"persona_required_for_agent_memory",status:400};spaceId=persona.spaceId;scope="agent";}
    else if(type==="space"){if(!space)return{ok:false,error:"space_required_for_space_memory",status:400};personaId=null;scope="space";}
    else if(type==="working"){scope=personaId?"agent_working":(spaceId?"space_working":"working");}
    else if(type==="episodic"){scope=taskId?"task":(personaId?"agent_episode":(spaceId?"space_episode":"episode"));}
    if(taskId){const task=state.tasks[taskId];if(!task||task.userId!==id)return{ok:false,error:"task_not_found",status:404};}
    const createdAt=now(),ttlMinutes=type==="working"?Math.max(5,Math.min(10080,Number(b.ttlMinutes)||1440)):null;
    const item={id:crypto.randomUUID(),userId:id,text,type,scope,spaceId,personaId,taskId,source:clean(b.source,80)||"manual",createdAt,expiresAt:ttlMinutes?createdAt+ttlMinutes*60000:null};
    state.memories.push(item);state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId,personaId,taskId,type:"memory_created",data:{memoryId:item.id,memoryType:type,scope},createdAt});persist();return{ok:true,item};
  }
  function listMemories(id,url){
    const type=clean(url.searchParams.get("type"),40).toLowerCase(),spaceId=clean(url.searchParams.get("spaceId"),60),personaId=clean(url.searchParams.get("personaId"),60),q=clean(url.searchParams.get("q"),500),limit=Math.max(1,Math.min(200,Number(url.searchParams.get("limit"))||100));
    let items=state.memories.filter(m=>m.userId===id&&memoryAlive(m));if(type)items=items.filter(m=>m.type===type);if(spaceId)items=items.filter(m=>m.spaceId===spaceId);if(personaId)items=items.filter(m=>m.personaId===personaId);if(q){const words=tokenizeMemory(q);items=items.filter(m=>words.some(w=>clean(m.text,12000).toLowerCase().includes(w)));}
    return items.sort((a,b)=>Number(b.createdAt||0)-Number(a.createdAt||0)).slice(0,limit);
  }
  function retrieveMemories(id,url){
    const q=clean(url.searchParams.get("q"),12000),spaceId=clean(url.searchParams.get("spaceId"),60)||null,personaId=clean(url.searchParams.get("personaId"),60)||null,limit=Math.max(1,Math.min(50,Number(url.searchParams.get("limit"))||12));
    if(spaceId&&!ownedSpace(id,spaceId))return{ok:false,error:"space_not_found",status:404};if(personaId&&!ownedPersona(id,personaId))return{ok:false,error:"persona_not_found",status:404};
    const persona=personaId?ownedPersona(id,personaId):null;if(persona&&spaceId&&persona.spaceId!==spaceId)return{ok:false,error:"persona_space_mismatch",status:400};
    return{ok:true,query:q,spaceId,personaId,items:rankMemories(id,personaId,spaceId,q,limit)};
  }
  async function runTask(id,b){const input=clean(b.input||b.task||b.text,12000);if(!input)return{status:400,body:{ok:false,error:"empty_task"}};const space=ownedSpace(id,clean(b.spaceId,60));if(!space)return{status:404,body:{ok:false,error:"space_not_found"}};const persona=ownedPersona(id,clean(b.personaId,60));if(!persona)return{status:404,body:{ok:false,error:"persona_not_found"}};if(persona.spaceId!==space.id)return{status:400,body:{ok:false,error:"persona_space_mismatch"}};if(!allows(id,persona.id,"model.call"))return{status:403,body:{ok:false,error:"permission_denied"}};const taskId=crypto.randomUUID(),started=now();const task={id:taskId,userId:id,spaceId:space.id,personaId:persona.id,input,status:"running",createdAt:started,startedAt:started};state.tasks[taskId]=task;persist();const ai=await callPersona(persona,input,clean(b.quality,20)||"auto"),ended=now();if(!ai.ok){task.status="failed";task.error=ai.error;task.completedAt=ended;const fitness={id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,success:0,quality:null,timeMs:ended-started,cost:0,revenue:0,ownerTimeMs:Number(b.ownerTimeMs||0),error:ai.error,createdAt:ended};state.fitnessRecords.push(fitness);state.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,type:"task_failed",data:{error:ai.error},createdAt:ended});persist();return{status:ai.error==="ai_not_configured"?503:502,body:{ok:false,error:ai.error,task,fitness,providers:ai.providers}};}task.status="completed";task.result=ai.reply;task.provider=ai.provider;task.model=ai.model;task.cost=0;task.completedAt=ended;const memory=allows(id,persona.id,"memory.write")?{id:crypto.randomUUID(),userId:id,text:`TASK: ${input}\nRESULT: ${ai.reply}`,type:"episodic",scope:"task",spaceId:space.id,personaId:persona.id,taskId,source:"task_cycle",createdAt:ended}:null;if(memory)state.memories.push(memory);const experience={id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,type:"task_completed",data:{provider:ai.provider,model:ai.model,cost:0},createdAt:ended};state.experienceEvents.push(experience);const fitness={id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,success:1,quality:null,timeMs:ended-started,cost:0,revenue:Number(b.revenue||0),ownerTimeMs:Number(b.ownerTimeMs||0),error:null,createdAt:ended};state.fitnessRecords.push(fitness);persona.xp+=10;persona.level=1+Math.floor(persona.xp/100);persona.updatedAt=ended;persist();return{status:200,body:{ok:true,cycle:["USER","SPACE","PERSONA","TASK","RESULT","MEMORY","EXPERIENCE","FITNESS"],task,memory,experience,fitness,persona:{id:persona.id,xp:persona.xp,level:persona.level}}};}
  async function handle(req,res,url){try{if(req.method==="OPTIONS")return json(res,200,{ok:true});const isGet=req.method==="GET",b=isGet?{}:await readJson(req),id=clean(isGet?url.searchParams.get("conversationId"):b.conversationId,60),secret=clean(isGet?url.searchParams.get("installSecret"):b.installSecret,180);if(!authOrCreate(id,secret))return json(res,403,{ok:false,error:"auth_failed"});if(req.method==="GET"&&url.pathname==="/api/gos/state")return json(res,200,{ok:true,version:VERSION,user:{id,authType:"install"},counts:{spaces:Object.values(state.spaces).filter(s=>s.userId===id&&!s.archivedAt).length,personas:Object.values(state.personas).filter(p=>p.userId===id&&!p.archivedAt).length,tasks:Object.values(state.tasks).filter(t=>t.userId===id).length,memories:state.memories.filter(x=>x.userId===id).length,experienceEvents:state.experienceEvents.filter(x=>x.userId===id).length,fitnessRecords:state.fitnessRecords.filter(x=>x.userId===id).length},providers:publicProviderStatus()});if(req.method==="GET"&&url.pathname==="/api/gos/spaces")return json(res,200,{ok:true,items:Object.values(state.spaces).filter(s=>s.userId===id&&!s.archivedAt)});if(req.method==="POST"&&url.pathname==="/api/gos/spaces"){const name=clean(b.name,120);if(!name)return json(res,400,{ok:false,error:"space_name_required"});const sid=crypto.randomUUID(),item={id:sid,userId:id,key:(clean(b.key,80)||name).toUpperCase().replace(/[^A-ZА-Я0-9]+/g,"_"),name,status:clean(b.status,20)||"CORE",isSystem:false,createdAt:now(),updatedAt:now()};state.spaces[sid]=item;persist();return json(res,201,{ok:true,item});}if(req.method==="GET"&&url.pathname==="/api/gos/personas"){const sid=clean(url.searchParams.get("spaceId"),60);return json(res,200,{ok:true,items:Object.values(state.personas).filter(p=>p.userId===id&&!p.archivedAt&&(!sid||p.spaceId===sid))});}if(req.method==="POST"&&url.pathname==="/api/gos/personas"){const out=createPersona(id,b);return json(res,out.ok?201:400,out);}const personaEdit=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})$/);if(req.method==="PATCH"&&personaEdit){const out=updatePersona(id,personaEdit[1],b);return json(res,out.ok?200:(out.status||400),out);}const personaClone=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})\/clone$/);if(req.method==="POST"&&personaClone){const out=clonePersona(id,personaClone[1],b);return json(res,out.ok?201:(out.status||400),out);}const personaArchive=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})\/archive$/);if(req.method==="POST"&&personaArchive){const out=archivePersona(id,personaArchive[1]);return json(res,out.ok?200:(out.status||400),out);}if(req.method==="GET"&&url.pathname==="/api/gos/model-providers")return json(res,200,{ok:true,items:publicProviderStatus()});if(req.method==="GET"&&url.pathname==="/api/gos/permissions")return json(res,200,{ok:true,items:state.permissions.filter(x=>x.userId===id)});if(req.method==="GET"&&url.pathname==="/api/gos/tasks")return json(res,200,{ok:true,items:Object.values(state.tasks).filter(t=>t.userId===id).sort((a,b)=>b.createdAt-a.createdAt)});if(req.method==="POST"&&url.pathname==="/api/gos/tasks/run"){if(!rateAllowed(`${id}:gos-task`))return json(res,429,{ok:false,error:"too_many_messages"});const out=await runTask(id,b);return json(res,out.status,out.body);}if(req.method==="GET"&&url.pathname==="/api/gos/memory/schema")return json(res,200,{ok:true,schema:memorySchema()});if(req.method==="POST"&&url.pathname==="/api/gos/memory"){const out=createMemory(id,b);return json(res,out.ok?201:(out.status||400),out);}if(req.method==="GET"&&url.pathname==="/api/gos/memory/retrieve"){const out=retrieveMemories(id,url);return json(res,out.ok?200:(out.status||400),out);}if(req.method==="GET"&&url.pathname==="/api/gos/memory")return json(res,200,{ok:true,items:listMemories(id,url)});if(req.method==="GET"&&url.pathname==="/api/gos/experience")return json(res,200,{ok:true,items:state.experienceEvents.filter(x=>x.userId===id).slice().sort((a,b)=>b.createdAt-a.createdAt)});if(req.method==="GET"&&url.pathname==="/api/gos/fitness")return json(res,200,{ok:true,items:state.fitnessRecords.filter(x=>x.userId===id).slice().sort((a,b)=>b.createdAt-a.createdAt)});if(req.method==="GET"&&url.pathname==="/api/gos/achievements")return json(res,200,{ok:true,items:state.achievements.filter(x=>x.userId===id)});return json(res,404,{ok:false,error:"gos_not_found"});}catch(e){console.error(e);return json(res,500,{ok:false,error:"gos_server_error",detail:process.env.NODE_ENV==="development"?e.message:undefined});}}
  return{handle,state};
}

module.exports={createStandaloneGos,VERSION};

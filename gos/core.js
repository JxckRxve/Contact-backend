"use strict";

const crypto = require("crypto");

const VERSION = "0.1.0";
const BASE_SPACES = [
  ["HOME","HOME","CORE"],["LIFE","LIFE","CORE"],["MONEY","MONEY","CORE"],["WORK","WORK","CORE"],
  ["CREATIVE","CREATIVE","CORE"],["RESEARCH","RESEARCH","CORE"],["GAME","GAME","CORE"],["ANALYSIS","ANALYSIS","CORE"],
  ["INTEL","INTEL","LAB"],["OPEN","OPEN","CORE"],["REALITY","REALITY","LAB"],["EVOLUTION","EVOLUTION","LAB"],
  ["SIMULATIONS","SIMULATIONS","LAB"],["MULTIVERSE","MULTIVERSE","LAB"]
];

function createGosCore({state,persist,clean,authOrCreate,rateAllowed,runAi,publicProviderStatus}) {
  function now(){ return Date.now(); }
  function ensureStore(){
    if (!state.gos || typeof state.gos !== "object") state.gos = {};
    for (const [k,v] of Object.entries({users:{},spaces:{},tasks:{},experienceEvents:[],achievements:[],permissions:[],fitnessRecords:[]})) {
      if (state.gos[k] == null) state.gos[k] = v;
    }
    return state.gos;
  }
  function ensureUser(id){
    const g=ensureStore(); let changed=false;
    if(!g.users[id]){g.users[id]={id,authType:"install",createdAt:now(),updatedAt:now()};changed=true;}
    for(const [key,name,status] of BASE_SPACES){
      if(!Object.values(g.spaces).some(s=>s.userId===id&&s.key===key&&!s.archivedAt)){
        const sid=crypto.randomUUID(); g.spaces[sid]={id:sid,userId:id,key,name,status,isSystem:true,createdAt:now(),updatedAt:now()}; changed=true;
      }
    }
    if(changed)persist(); return g.users[id];
  }
  function getHome(id){ensureUser(id);return Object.values(state.gos.spaces).find(s=>s.userId===id&&s.key==="HOME"&&!s.archivedAt)||null;}
  function ownedSpace(id,sid){const s=ensureStore().spaces[sid];return s&&s.userId===id&&!s.archivedAt?s:null;}
  function normalizePersona(id,p){
    if(!p||p.conversationId!==id)return null; let changed=false;
    if(!p.spaceId){p.spaceId=getHome(id)?.id||null;changed=true;}
    if(!p.role){p.role=p.kind==="mentor"?"mentor":"persona";changed=true;}
    if(!p.personality){p.personality=p.description||"";changed=true;}
    if(!Array.isArray(p.tools)){p.tools=[];changed=true;}
    if(!Array.isArray(p.permissionIds)){p.permissionIds=[];changed=true;}
    if(!Number.isFinite(p.xp)){p.xp=0;changed=true;}
    if(!Number.isFinite(p.level)||p.level<1){p.level=1;changed=true;}
    if(!Array.isArray(p.achievementIds)){p.achievementIds=[];changed=true;}
    if(!p.status){p.status="active";changed=true;}
    if(!p.genome||typeof p.genome!=="object"){
      p.genome={version:"0.1",role:p.role,prompt:"",planning:"direct",memoryStrategy:"scoped_relevant_recent",model:"auto",tools:[...p.tools],communicationStyle:"natural",parentPersonaId:null,generation:0,mutations:[]};changed=true;
    }
    if(changed)persist(); return p;
  }
  function ownedPersona(id,pid){const p=normalizePersona(id,state.personas[pid]);return p&&!p.archivedAt?p:null;}
  function addPermission(id,pid,action,effect="allow"){
    const item={id:crypto.randomUUID(),userId:id,personaId:pid,action,effect,createdAt:now()}; ensureStore().permissions.push(item);
    const p=state.personas[pid]; if(p){p.permissionIds=p.permissionIds||[];p.permissionIds.push(item.id);} return item;
  }
  function allows(id,pid,action){const xs=ensureStore().permissions.filter(x=>x.userId===id&&x.personaId===pid&&x.action===action);return !xs.some(x=>x.effect==="deny");}
  function addMemory(id,{text,type="episodic",scope="task",spaceId=null,personaId=null,taskId=null,source="gos"}){
    if(!state.memories[id])state.memories[id]=[];
    const item={id:crypto.randomUUID(),userId:id,text:clean(text,12000),type,scope,spaceId,personaId,taskId,source,createdAt:now()}; state.memories[id].push(item); return item;
  }
  function addFitness(data){const item={id:crypto.randomUUID(),createdAt:now(),quality:null,revenue:0,ownerTimeMs:0,...data};ensureStore().fitnessRecords.push(item);return item;}
  function createPersona(id,b){
    const space=ownedSpace(id,clean(b.spaceId,60))||getHome(id); if(!space)return{ok:false,error:"space_not_found"};
    const pid=crypto.randomUUID(); const role=clean(b.role,120)||"assistant"; const personality=clean(b.personality,3000)||clean(b.description,3000)||"Natural, useful, consistent.";
    const tools=Array.isArray(b.tools)?b.tools.map(x=>clean(x,80)).filter(Boolean).slice(0,30):[];
    const p={id:pid,conversationId:id,spaceId:space.id,kind:clean(b.kind,40)||"mentor",buildMode:clean(b.buildMode,40)||"quick",name:clean(b.name,120)||role,role,relationship:clean(b.relationship,500),description:personality,personality,speech:clean(b.speech,3000),memories:clean(b.memories,6000),neverSay:clean(b.neverSay,3000),visualProfile:{},core:{visual:0,character:35,voice:0,memory:25,relationship:20},tools,permissionIds:[],xp:0,level:1,achievementIds:[],status:"active",genome:{version:"0.1",role,prompt:clean(b.prompt,4000),planning:clean(b.planning,60)||"direct",memoryStrategy:"scoped_relevant_recent",model:clean(b.model,80)||"auto",tools:[...tools],communicationStyle:clean(b.communicationStyle,120)||"natural",parentPersonaId:null,generation:0,mutations:[]},createdAt:now(),updatedAt:now()};
    state.personas[pid]=p; addPermission(id,pid,"model.call"); addPermission(id,pid,"memory.write");
    ensureStore().experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:pid,type:"persona_created",data:{name:p.name,role:p.role},createdAt:now()}); persist(); return{ok:true,persona:p};
  }
  async function runTask(id,b){
    const input=clean(b.input||b.task||b.text,12000); if(!input)return{status:400,body:{ok:false,error:"empty_task"}};
    const space=ownedSpace(id,clean(b.spaceId,60)); if(!space)return{status:404,body:{ok:false,error:"space_not_found"}};
    const persona=ownedPersona(id,clean(b.personaId,60)); if(!persona)return{status:404,body:{ok:false,error:"persona_not_found"}};
    if(persona.spaceId!==space.id)return{status:400,body:{ok:false,error:"persona_space_mismatch"}};
    if(!allows(id,persona.id,"model.call"))return{status:403,body:{ok:false,error:"permission_denied"}};
    const g=ensureStore(), taskId=crypto.randomUUID(), started=now();
    const task={id:taskId,userId:id,spaceId:space.id,personaId:persona.id,input,status:"running",createdAt:started,startedAt:started,result:null,provider:null,model:null,cost:0,error:null};g.tasks[taskId]=task;persist();
    const ai=await runAi({id,text:input,task:"gos_task",quality:clean(b.quality,20)||"auto",personaId:persona.id}); const ended=now();
    if(!ai.ok){task.status="failed";task.error=ai.error;task.completedAt=ended;const fitness=addFitness({userId:id,spaceId:space.id,personaId:persona.id,taskId,success:0,timeMs:ended-started,cost:0,error:ai.error});g.experienceEvents.push({id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,type:"task_failed",data:{error:ai.error},createdAt:ended});persist();return{status:ai.error==="ai_not_configured"?503:502,body:{ok:false,error:ai.error,task,fitness,providers:ai.providers}};}
    task.status="completed";task.result=ai.reply;task.provider=ai.provider;task.model=ai.model;task.cost=ai.cost||0;task.completedAt=ended;
    const memory=allows(id,persona.id,"memory.write")?addMemory(id,{text:`TASK: ${input}\nRESULT: ${ai.reply}`,spaceId:space.id,personaId:persona.id,taskId,source:"task_cycle"}):null;
    const experience={id:crypto.randomUUID(),userId:id,spaceId:space.id,personaId:persona.id,taskId,type:"task_completed",data:{provider:ai.provider,model:ai.model,cost:ai.cost||0},createdAt:ended};g.experienceEvents.push(experience);
    const fitness=addFitness({userId:id,spaceId:space.id,personaId:persona.id,taskId,success:1,timeMs:ended-started,cost:ai.cost||0,revenue:0,ownerTimeMs:Number(b.ownerTimeMs||0),error:null});persona.xp=Number(persona.xp||0)+10;persona.level=1+Math.floor(persona.xp/100);persona.updatedAt=ended;persist();
    return{status:200,body:{ok:true,cycle:["USER","SPACE","PERSONA","TASK","RESULT","MEMORY","EXPERIENCE","FITNESS"],task,memory,experience,fitness,persona:{id:persona.id,xp:persona.xp,level:persona.level}}};
  }
  async function handle(req,res,url,json){
    const isGet=req.method==="GET"; const b=isGet?{}:await (async()=>{let raw="";for await(const c of req){raw+=c;if(raw.length>256000)throw new Error("payload_too_large");}return raw?JSON.parse(raw):{};})();
    const id=clean(isGet?url.searchParams.get("conversationId"):b.conversationId,60), secret=clean(isGet?url.searchParams.get("installSecret"):b.installSecret,180);
    if(!authOrCreate(id,secret))return json(res,403,{ok:false,error:"auth_failed"}); ensureUser(id); const g=ensureStore();
    if(req.method==="GET"&&url.pathname==="/api/gos/state")return json(res,200,{ok:true,version:VERSION,user:g.users[id],counts:{spaces:Object.values(g.spaces).filter(s=>s.userId===id&&!s.archivedAt).length,personas:Object.values(state.personas).filter(p=>p.conversationId===id&&!p.archivedAt).length,tasks:Object.values(g.tasks).filter(t=>t.userId===id).length,memories:(state.memories[id]||[]).length,experienceEvents:g.experienceEvents.filter(x=>x.userId===id).length,fitnessRecords:g.fitnessRecords.filter(x=>x.userId===id).length},providers:publicProviderStatus()});
    if(req.method==="GET"&&url.pathname==="/api/gos/spaces")return json(res,200,{ok:true,items:Object.values(g.spaces).filter(s=>s.userId===id&&!s.archivedAt)});
    if(req.method==="POST"&&url.pathname==="/api/gos/spaces"){const name=clean(b.name,120);if(!name)return json(res,400,{ok:false,error:"space_name_required"});const sid=crypto.randomUUID();const item={id:sid,userId:id,key:(clean(b.key,80)||name).toUpperCase().replace(/[^A-ZА-Я0-9]+/g,"_"),name,status:clean(b.status,20)||"CORE",isSystem:false,createdAt:now(),updatedAt:now()};g.spaces[sid]=item;persist();return json(res,201,{ok:true,item});}
    if(req.method==="GET"&&url.pathname==="/api/gos/personas"){const sid=clean(url.searchParams.get("spaceId"),60);return json(res,200,{ok:true,items:Object.values(state.personas).filter(p=>p.conversationId===id&&!p.archivedAt).map(p=>normalizePersona(id,p)).filter(p=>!sid||p.spaceId===sid)});}
    if(req.method==="POST"&&url.pathname==="/api/gos/personas"){const out=createPersona(id,b);return json(res,out.ok?201:400,out);}
    if(req.method==="GET"&&url.pathname==="/api/gos/model-providers")return json(res,200,{ok:true,items:publicProviderStatus()});
    if(req.method==="GET"&&url.pathname==="/api/gos/permissions")return json(res,200,{ok:true,items:g.permissions.filter(x=>x.userId===id)});
    if(req.method==="GET"&&url.pathname==="/api/gos/tasks")return json(res,200,{ok:true,items:Object.values(g.tasks).filter(t=>t.userId===id).sort((a,b)=>b.createdAt-a.createdAt)});
    if(req.method==="POST"&&url.pathname==="/api/gos/tasks/run"){if(!rateAllowed(`${id}:gos-task`))return json(res,429,{ok:false,error:"too_many_messages"});const out=await runTask(id,b);return json(res,out.status,out.body);}
    if(req.method==="GET"&&url.pathname==="/api/gos/memory")return json(res,200,{ok:true,items:(state.memories[id]||[]).filter(x=>!url.searchParams.get("spaceId")||x.spaceId===url.searchParams.get("spaceId")).filter(x=>!url.searchParams.get("personaId")||x.personaId===url.searchParams.get("personaId")).slice(-100).reverse()});
    if(req.method==="GET"&&url.pathname==="/api/gos/experience")return json(res,200,{ok:true,items:g.experienceEvents.filter(x=>x.userId===id).sort((a,b)=>b.createdAt-a.createdAt)});
    if(req.method==="GET"&&url.pathname==="/api/gos/fitness")return json(res,200,{ok:true,items:g.fitnessRecords.filter(x=>x.userId===id).sort((a,b)=>b.createdAt-a.createdAt)});
    if(req.method==="GET"&&url.pathname==="/api/gos/achievements")return json(res,200,{ok:true,items:g.achievements.filter(x=>x.userId===id)});
    return json(res,404,{ok:false,error:"gos_not_found"});
  }
  function deleteUser(id){const g=ensureStore();delete g.users[id];for(const [k,v] of Object.entries(g.spaces))if(v.userId===id)delete g.spaces[k];for(const [k,v] of Object.entries(g.tasks))if(v.userId===id)delete g.tasks[k];g.experienceEvents=g.experienceEvents.filter(x=>x.userId!==id);g.achievements=g.achievements.filter(x=>x.userId!==id);g.permissions=g.permissions.filter(x=>x.userId!==id);g.fitnessRecords=g.fitnessRecords.filter(x=>x.userId!==id);}
  return {version:VERSION,ensureUser,normalizePersona,handle,deleteUser};
}

module.exports={createGosCore,VERSION};

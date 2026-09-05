from pathlib import Path

p=Path("gos/standalone.js")
s=p.read_text()
if "function updatePersona(id,pid,b)" not in s:
    marker="  async function runTask"
    i=s.find(marker)
    if i<0: raise SystemExit("runTask marker missing")
    block=r'''  function updatePersona(id,pid,b){
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
'''
    s=s[:i]+block+s[i:]

if "personaClone=url.pathname.match" not in s:
    marker='if(req.method==="GET"&&url.pathname==="/api/gos/model-providers")'
    i=s.find(marker)
    if i<0: raise SystemExit("model providers route marker missing")
    routes=r'''const personaEdit=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})$/);if(req.method==="PATCH"&&personaEdit){const out=updatePersona(id,personaEdit[1],b);return json(res,out.ok?200:(out.status||400),out);}const personaClone=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})\/clone$/);if(req.method==="POST"&&personaClone){const out=clonePersona(id,personaClone[1],b);return json(res,out.ok?201:(out.status||400),out);}const personaArchive=url.pathname.match(/^\/api\/gos\/personas\/([0-9a-fA-F-]{30,50})\/archive$/);if(req.method==="POST"&&personaArchive){const out=archivePersona(id,personaArchive[1]);return json(res,out.ok?200:(out.status||400),out);}'''
    s=s[:i]+routes+s[i:]
p.write_text(s)

p=Path("tests/gos-core-smoke.js")
s=p.read_text()
if "persona edit failed" not in s:
    marker='    assert(persona.status === 201 && persona.body.persona?.id, "persona creation failed");'
    i=s.find(marker)
    if i<0: raise SystemExit("persona assertion marker missing")
    insert=r'''
    const originalPersonaId = persona.body.persona.id;
    const edited = await request(`/api/gos/personas/${originalPersonaId}`, {method:"PATCH",body:{conversationId,installSecret,name:"Core Tester Edited",role:"senior analyst",personality:"Precise, concise, evidence-first",tools:["research"],planning:"structured",communicationStyle:"direct"}});
    assert(edited.status === 200 && edited.body.persona?.name === "Core Tester Edited", "persona edit failed");
    assert(edited.body.persona?.genome?.planning === "structured", "genome edit failed");
    const cloned = await request(`/api/gos/personas/${originalPersonaId}/clone`, {method:"POST",body:{conversationId,installSecret,name:"Core Tester Clone"}});
    assert(cloned.status === 201 && cloned.body.persona?.id && cloned.body.persona.id !== originalPersonaId, "persona clone failed");
    assert(cloned.body.persona?.xp === 0 && cloned.body.persona?.level === 1, "clone progress must reset");
    assert(cloned.body.persona?.cloneOfPersonaId === originalPersonaId, "clone source missing");
    const archived = await request(`/api/gos/personas/${originalPersonaId}/archive`, {method:"POST",body:{conversationId,installSecret}});
    assert(archived.status === 200 && archived.body.persona?.status === "archived", "persona archive failed");
    const activePersonas = await request(`/api/gos/personas?conversationId=${conversationId}&installSecret=${installSecret}&spaceId=${home.id}`);
    assert(!activePersonas.body.items.some(x => x.id === originalPersonaId), "archived persona still active");
    assert(activePersonas.body.items.some(x => x.id === cloned.body.persona.id), "cloned persona missing");
'''
    s=s[:i+len(marker)]+insert+s[i+len(marker):]
    old='body:{conversationId,installSecret,spaceId:home.id,personaId:persona.body.persona.id,input:"Return the first working G-OS result."}'
    new='body:{conversationId,installSecret,spaceId:home.id,personaId:cloned.body.persona.id,input:"Return the first working G-OS result."}'
    if old not in s: raise SystemExit("cycle persona marker missing")
    s=s.replace(old,new,1)
p.write_text(s)

"use strict";

const http = require("http");
const {spawn} = require("child_process");
const crypto = require("crypto");
const os = require("os");
const path = require("path");
const fs = require("fs");

const APP_PORT = 18080;
const MOCK_PORT = 18081;
const base = `http://127.0.0.1:${APP_PORT}`;
const conversationId = crypto.randomUUID();
const installSecret = crypto.randomUUID() + crypto.randomUUID();
const suffix = crypto.randomUUID();
const dbPath = path.join(os.tmpdir(), `contact-${suffix}.json`);
const gosDbPath = path.join(os.tmpdir(), `gos-${suffix}.json`);

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(pathname, options = {}) {
  const r = await fetch(base + pathname, {
    method: options.method || "GET",
    headers: {"Content-Type":"application/json"},
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const text = await r.text();
  let body;
  try { body = JSON.parse(text); } catch { body = {raw:text}; }
  return {status:r.status, body};
}

function wait(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

async function waitForApp() {
  for (let i = 0; i < 50; i++) {
    try {
      const r = await fetch(base + "/health");
      if (r.ok) return;
    } catch {}
    await wait(100);
  }
  throw new Error("app_not_ready");
}

async function main() {
  const mock = http.createServer(async (req, res) => {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    const body = raw ? JSON.parse(raw) : {};
    const lastInput = Array.isArray(body.input) ? body.input[body.input.length - 1] : null;
    const userText = typeof lastInput?.content === "string" ? lastInput.content : "task";

    assert(body.model === "mock-responses-model", "Responses model missing");
    assert(typeof body.instructions === "string" && body.instructions.includes("G-OS"), "Responses instructions missing");
    assert(body.store === false, "Responses store must be false");

    res.writeHead(200, {"Content-Type":"application/json"});
    res.end(JSON.stringify({
      id:"resp_mock",
      model:"mock-responses-model",
      output:[{
        id:"msg_mock",
        type:"message",
        role:"assistant",
        content:[{type:"output_text",text:`MOCK_RESULT: ${userText}`,annotations:[]}]
      }],
      usage:{input_tokens:10,output_tokens:5,total_tokens:15}
    }));
  });
  await new Promise(resolve => mock.listen(MOCK_PORT, "127.0.0.1", resolve));

  const app = spawn(process.execPath, ["gos-server.js"], {
    cwd: path.join(__dirname, ".."),
    env: {
      ...process.env,
      PORT:String(APP_PORT),
      DB_PATH:dbPath,
      GOS_DB_PATH:gosDbPath,
      CONTACT_AI_PRIMARY_ENDPOINT:`http://127.0.0.1:${MOCK_PORT}/v1/responses`,
      CONTACT_AI_PRIMARY_API_KEY:"test-key",
      CONTACT_AI_PRIMARY_MODEL:"mock-responses-model",
      CONTACT_AI_PRIMARY_KIND:"openai-responses",
      TELEGRAM_BOT_TOKEN:"",
      TELEGRAM_ADMIN_CHAT_ID:"",
      NODE_ENV:"test"
    },
    stdio:["ignore","pipe","pipe"]
  });

  let stderr = "";
  app.stderr.on("data", d => { stderr += d.toString(); });

  try {
    await waitForApp();

    const wallet = await request(`/api/wallet?conversationId=${conversationId}&installSecret=${installSecret}`);
    assert(wallet.status === 200 && wallet.body.ok, "legacy wallet failed");

    const spaces = await request(`/api/gos/spaces?conversationId=${conversationId}&installSecret=${installSecret}`);
    assert(spaces.status === 200 && spaces.body.ok, "G-OS spaces failed");
    const home = spaces.body.items.find(x => x.key === "HOME");
    assert(home, "HOME space missing");

    const providers = await request(`/api/gos/model-providers?conversationId=${conversationId}&installSecret=${installSecret}`);
    assert(providers.status === 200 && providers.body.items?.[0]?.kind === "openai-responses", "Responses provider status missing");
    assert(providers.body.items?.[0]?.configured === true, "Responses provider not configured in test");

    const persona = await request("/api/gos/personas", {
      method:"POST",
      body:{conversationId,installSecret,spaceId:home.id,name:"Core Tester",role:"analyst",personality:"Precise and concise"}
    });
    assert(persona.status === 201 && persona.body.persona?.id, "persona creation failed");
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


    const cycle = await request("/api/gos/tasks/run", {
      method:"POST",
      body:{conversationId,installSecret,spaceId:home.id,personaId:cloned.body.persona.id,input:"Return the first working G-OS result."}
    });
    assert(cycle.status === 200 && cycle.body.ok, "task cycle failed");
    assert(cycle.body.task?.status === "completed", "task not completed");
    assert(cycle.body.task?.result?.startsWith("MOCK_RESULT:"), "model result missing");
    assert(cycle.body.memory?.taskId === cycle.body.task.id, "memory missing");
    assert(cycle.body.experience?.taskId === cycle.body.task.id, "experience missing");
    assert(cycle.body.fitness?.taskId === cycle.body.task.id && cycle.body.fitness.success === 1, "fitness missing");

    const state = await request(`/api/gos/state?conversationId=${conversationId}&installSecret=${installSecret}`);
    assert(state.body.counts.tasks === 1, "task persistence failed");
    assert(state.body.counts.memories === 1, "memory persistence failed");
    assert(state.body.counts.fitnessRecords === 1, "fitness persistence failed");

    console.log("PASS G-OS CORE + OPENAI RESPONSES: USER -> SPACE -> PERSONA -> TASK -> RESULT -> MEMORY -> EXPERIENCE -> FITNESS");
    console.log(JSON.stringify({legacyWallet:wallet.body.balance,spaces:spaces.body.items.length,provider:providers.body.items[0],result:cycle.body.task.result,counts:state.body.counts}, null, 2));
  } finally {
    app.kill("SIGTERM");
    mock.close();
    for (const p of [dbPath, gosDbPath]) { try { fs.unlinkSync(p); } catch {} }
  }

  if (stderr) process.stderr.write(stderr);
}

main().catch(err => {
  console.error(err);
  process.exitCode = 1;
});

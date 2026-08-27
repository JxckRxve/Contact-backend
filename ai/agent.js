"use strict";

const {chooseProvider} = require("./router");
const {callProvider} = require("./providers");

const CORE_SYSTEM = `You are CONTACT Core, an execution-oriented AI inside the CONTACT product.
Your job is to understand the user's goal, produce useful results, and when asked for a plan, break the work into concrete verifiable steps.
Do not pretend an external action was completed unless a connected tool actually performed it.
Preserve user agency for irreversible actions.
Reply in the user's language.`;

function safeJsonParse(text) {
  const raw = String(text || "").trim();
  try { return JSON.parse(raw); } catch {}
  const start = raw.indexOf("{");
  const end = raw.lastIndexOf("}");
  if (start >= 0 && end > start) {
    try { return JSON.parse(raw.slice(start, end + 1)); } catch {}
  }
  return null;
}

async function makePlan(goal, context, quality = "auto") {
  const provider = chooseProvider({quality});
  if (!provider) return {ok:false,error:"ai_not_configured"};

  const prompt = `GOAL:\n${goal}\n\nCURRENT STATE:\n${context || "No additional state."}\n\nReturn JSON only with this shape:\n{"summary":"...","steps":[{"id":"1","action":"...","requiresTool":false,"requiresApproval":false,"successCheck":"..."}],"doneWhen":"..."}`;
  const result = await callProvider(provider, {
    messages:[
      {role:"system",content:CORE_SYSTEM},
      {role:"user",content:prompt}
    ],
    temperature:0.3,
    maxTokens:1400
  });

  const parsed = safeJsonParse(result.text);
  return {
    ok:true,
    provider:result.provider,
    model:result.model,
    plan:parsed || {summary:result.text,steps:[],doneWhen:"Review generated plan."}
  };
}

module.exports = {CORE_SYSTEM, makePlan};

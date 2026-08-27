"use strict";

const {getProviders, isConfigured} = require("./providers");

function chooseProvider({quality = "auto", allowLocal = true} = {}) {
  const providers = getProviders();
  const byId = Object.fromEntries(providers.map(p => [p.id, p]));

  const order = quality === "local"
    ? ["local", "primary", "secondary"]
    : quality === "fast"
      ? ["secondary", "primary", "local"]
      : quality === "max"
        ? ["primary", "secondary", "local"]
        : ["primary", "secondary", "local"];

  for (const id of order) {
    if (id === "local" && !allowLocal) continue;
    const p = byId[id];
    if (isConfigured(p)) return p;
  }
  return null;
}

function classifyTask(task) {
  const t = String(task || "chat").toLowerCase();
  if (["chat","deep_chat","persona_chat","plan","reasoning","persona_refine"].includes(t)) return "text";
  if (["image","portrait","vision","video","voice","3d","code"].includes(t)) return t;
  return "text";
}

module.exports = {chooseProvider, classifyTask};

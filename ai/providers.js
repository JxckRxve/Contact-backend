"use strict";

function cleanBaseUrl(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

function providerFromEnv(prefix, id, label) {
  return {
    id,
    label,
    endpoint: cleanBaseUrl(process.env[`${prefix}_ENDPOINT`]),
    apiKey: String(process.env[`${prefix}_API_KEY`] || ""),
    model: String(process.env[`${prefix}_MODEL`] || ""),
    kind: String(process.env[`${prefix}_KIND`] || "openai-compatible")
  };
}

function getProviders() {
  const primary = providerFromEnv("CONTACT_AI_PRIMARY", "primary", "Primary");
  // Backward-compatible migration from the original PERSONA LLM variables.
  if (!primary.endpoint && process.env.LLM_ENDPOINT) primary.endpoint = cleanBaseUrl(process.env.LLM_ENDPOINT);
  if (!primary.apiKey && process.env.LLM_API_KEY) primary.apiKey = String(process.env.LLM_API_KEY);
  if (!primary.model && process.env.LLM_MODEL) primary.model = String(process.env.LLM_MODEL);
  if (process.env.LLM_KIND) primary.kind = String(process.env.LLM_KIND);
  return [
    primary,
    providerFromEnv("CONTACT_AI_SECONDARY", "secondary", "Secondary"),
    providerFromEnv("CONTACT_AI_LOCAL", "local", "Local / self-hosted")
  ];
}

function isConfigured(provider) {
  if (!provider) return false;
  if (provider.kind === "local-openai-compatible") {
    return Boolean(provider.endpoint && provider.model);
  }
  return Boolean(provider.endpoint && provider.model && provider.apiKey);
}

function publicProviderStatus() {
  return getProviders().map(p => ({
    id: p.id,
    label: p.label,
    kind: p.kind,
    model: p.model || null,
    configured: isConfigured(p)
  }));
}

function providerHeaders(provider) {
  const headers = {"Content-Type":"application/json"};
  if (provider.apiKey) headers.Authorization = `Bearer ${provider.apiKey}`;
  return headers;
}

async function parseProviderResponse(r) {
  const raw = await r.text();
  let data = null;
  try { data = JSON.parse(raw); } catch { data = {raw}; }
  if (!r.ok) {
    const err = new Error(`provider_http_${r.status}`);
    err.code = "provider_http_error";
    err.status = r.status;
    err.providerBody = data;
    throw err;
  }
  return data;
}

function requireConfigured(provider) {
  if (isConfigured(provider)) return;
  const err = new Error(`provider_not_configured:${provider?.id || "unknown"}`);
  err.code = "provider_not_configured";
  throw err;
}

async function callOpenAICompatible(provider, payload) {
  requireConfigured(provider);
  const r = await fetch(provider.endpoint, {
    method: "POST",
    headers: providerHeaders(provider),
    body: JSON.stringify({
      model: provider.model,
      messages: payload.messages,
      temperature: payload.temperature ?? 0.7,
      max_tokens: payload.maxTokens ?? 1200,
      stream: false
    })
  });
  const data = await parseProviderResponse(r);
  const text = data?.choices?.[0]?.message?.content
    ?? data?.choices?.[0]?.text
    ?? data?.output_text
    ?? "";
  return {
    text: String(text || ""),
    model: provider.model,
    provider: provider.id,
    rawUsage: data?.usage || null
  };
}

function responsesText(data) {
  if (typeof data?.output_text === "string" && data.output_text) return data.output_text;
  const parts = [];
  for (const item of (data?.output || [])) {
    if (item?.type !== "message") continue;
    for (const content of (item.content || [])) {
      if (content?.type === "output_text" && content.text) parts.push(String(content.text));
    }
  }
  return parts.join("\n");
}

async function callOpenAIResponses(provider, payload) {
  requireConfigured(provider);
  const messages = Array.isArray(payload.messages) ? payload.messages : [];
  const instructions = messages
    .filter(m => ["system", "developer"].includes(m.role))
    .map(m => String(m.content || ""))
    .filter(Boolean)
    .join("\n\n");
  const input = messages
    .filter(m => !["system", "developer"].includes(m.role))
    .map(m => ({
      role: m.role === "assistant" ? "assistant" : "user",
      content: String(m.content || "")
    }));

  const body = {
    model: provider.model,
    input,
    max_output_tokens: payload.maxTokens ?? 1200,
    store: false
  };
  if (instructions) body.instructions = instructions;

  const r = await fetch(provider.endpoint, {
    method: "POST",
    headers: providerHeaders(provider),
    body: JSON.stringify(body)
  });
  const data = await parseProviderResponse(r);
  return {
    text: responsesText(data),
    model: data?.model || provider.model,
    provider: provider.id,
    rawUsage: data?.usage || null
  };
}

async function callProvider(provider, payload) {
  if (["openai-compatible", "local-openai-compatible"].includes(provider.kind)) {
    return callOpenAICompatible(provider, payload);
  }
  if (provider.kind === "openai-responses") {
    return callOpenAIResponses(provider, payload);
  }
  const err = new Error(`unsupported_provider_kind:${provider.kind}`);
  err.code = "unsupported_provider_kind";
  throw err;
}

module.exports = {
  getProviders,
  isConfigured,
  publicProviderStatus,
  callProvider
};

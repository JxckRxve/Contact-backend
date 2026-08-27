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
  // Backward-compatible migration from the old PERSONA LLM variables.
  if (!primary.endpoint && process.env.LLM_ENDPOINT) primary.endpoint = cleanBaseUrl(process.env.LLM_ENDPOINT);
  if (!primary.apiKey && process.env.LLM_API_KEY) primary.apiKey = String(process.env.LLM_API_KEY);
  if (!primary.model && process.env.LLM_MODEL) primary.model = String(process.env.LLM_MODEL);
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

async function callOpenAICompatible(provider, payload) {
  if (!isConfigured(provider)) {
    const err = new Error(`provider_not_configured:${provider?.id || "unknown"}`);
    err.code = "provider_not_configured";
    throw err;
  }

  const headers = {"Content-Type":"application/json"};
  if (provider.apiKey) headers.Authorization = `Bearer ${provider.apiKey}`;

  const r = await fetch(provider.endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify({
      model: provider.model,
      messages: payload.messages,
      temperature: payload.temperature ?? 0.7,
      max_tokens: payload.maxTokens ?? 1200,
      stream: false
    })
  });

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

async function callProvider(provider, payload) {
  if (["openai-compatible", "local-openai-compatible"].includes(provider.kind)) {
    return callOpenAICompatible(provider, payload);
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

# CURRENT STATE — CONTACT / G-OS

Updated: 2026-09-05

## WORKING

- **Backend:** Node.js HTTP server with zero runtime npm dependencies (`server.js`).
- **Android:** native Android / Kotlin / Jetpack Compose app in `app/`.
- **Android CI:** GitHub Actions debug APK build has previously completed successfully.
- **Render:** Blueprint deployment exists; public Android base URL points to `https://contact-backend-u3ug.onrender.com/`.
- **Authentication:** install-scoped UUID + long install secret stored in Android SharedPreferences. This is device/install identity, not account auth.
- **CONTACT → Developer:** message send, polling/readback and Telegram admin bridge.
- **HIGHER:** separate private-style channel using the same human bridge.
- **UNLOAD / Drop the Burden DNA:** Android UI exists as UNLOAD. It is not a separate autonomous AI module.
- **PERSONA legacy API:** create Persona and send Persona messages to configured AI provider.
- **Wallet / C-coins:** local backend balance, daily claim, reserve give/take, feedback reward.
- **AI abstraction:** primary, secondary and local OpenAI-compatible providers with routing.
- **Legacy Memory API:** manual save/read of user memory.
- **Public landing:** `public/index.html`.
- **G-OS Core v0.1 backend layer:** separate `/api/gos/*` layer added without replacing legacy CONTACT routes.
- **G-OS base Spaces:** HOME, LIFE, MONEY, WORK, CREATIVE, RESEARCH, GAME, ANALYSIS, INTEL, OPEN, REALITY, EVOLUTION, SIMULATIONS, MULTIVERSE.
- **G-OS Persona v0.1:** role, personality, Space, tools, permissions, XP, level and Genome JSON.
- **G-OS task cycle:** USER → SPACE → PERSONA → TASK → MODEL → RESULT → MEMORY → EXPERIENCE → FITNESS.
- **Fitness history:** stored independently from Persona.
- **G-OS integration smoke test:** committed in `tests/gos-core-smoke.js` and executed by `.github/workflows/backend.yml`.

## PARTIAL

- **Persona UI:** legacy CONTACT persona creator exists; G-OS multi-Persona / Space management UI is not connected yet.
- **Memory Core:** G-OS task results create scoped episodic memory and retrieval exists for task prompt context; full Working / Personal / Agent / Space memory UX and lifecycle are not complete.
- **Achievements:** schema/storage endpoint exists in G-OS Core, but event rules and UI are not implemented yet.
- **Permission system:** model.call and memory.write are stored for new G-OS Personas; no permission editor UI yet.
- **ModelProvider:** backend abstraction is functional; provider management UI is absent.
- **Agent Genome v0.1:** JSON exists; clone/edit/archive/evolution controls are not implemented yet.
- **Render persistence:** legacy CONTACT and G-OS currently use JSON files on service filesystem. This is acceptable for prototype testing, not production durability.
- **File attachment:** Android picker exists, but selected attachment is not uploaded to backend yet.
- **Payments:** placeholder URLs remain in Android config. YooKassa production flow is not connected.

## BROKEN / NOT VERIFIED

- **Production AI configuration:** depends on Render environment variables; repository does not prove a provider key/endpoint is currently configured in production.
- **Production G-OS Core deploy:** Render auto-deploy must complete after the new wrapper change before marking CONNECTED/READY.
- **Telegram runtime:** code path exists, but current bot token/runtime health is not verified by CI.
- **HIRE:** no active HIRE implementation was found in the current repository.

## DEPRECATED / LEGACY NAMES

- Android package namespace still contains `svyazsbogom` from the earliest concept.
- Product UI still says CONTACT. This is intentionally preserved until G-OS shell/navigation is connected.
- HIGHER remains as CONTACT DNA, but is not treated as evidence of supernatural contact.

## REUSABLE

- `server.js` legacy CONTACT backend.
- `ai/providers.js`, `ai/router.js`, `ai/agent.js`.
- Android identity and Retrofit client.
- Compose visual system, palettes, motion and portal cards.
- Telegram bridge.
- Persona creation UX.
- C-coins/wallet mechanics.
- Render/GitHub Actions infrastructure.
- Public landing page.

## RULE

Do not delete or rewrite legacy CONTACT functionality merely to make G-OS cleaner. New G-OS functionality should wrap, migrate or replace legacy parts only after a tested equivalent exists.

# Voxora AI — Backend (Spring Boot)

Short summary

- Purpose: Core Spring Boot backend for Voxora AI — a multi-tenant Voice OS for Indian businesses.
- Tech: Java 21, Spring Boot 3.2+, Spring Web/WebSockets, Spring Data JPA (MySQL), Redis, Twilio, Deepgram, Anthropic Claude, ElevenLabs, Razorpay.

Quick start (Windows PowerShell)

Prerequisites

- Java 21 (SDK) installed and `JAVA_HOME` set.
- Git (optional)
- Internet access for external APIs and Maven dependencies.

Build & run (development)

Open PowerShell in the project root (where `mvnw.cmd` lives) and run:

```powershell
# build
.\mvnw.cmd -DskipTests clean package

# run
.\mvnw.cmd spring-boot:run
```

Run tests

```powershell
.\mvnw.cmd test
```

Project layout (important files)

- `pom.xml` — Maven build.
- `mvnw` / `mvnw.cmd` — Maven wrapper.
- `src/main/java/.../VoxoraAiApplication.java` — Spring Boot application.
- `src/main/resources/application.properties` — app configuration.
- `Voxora architecture` — project architecture and implementation roadmap (Telephony handshake, WebSocket audio pipeline, AI orchestration, payments, HITL dashboard).

Key endpoints (to implement / expected)

- POST `/api/voice/incoming` — Twilio webhook that returns TwiML instructing Twilio to open a WebSocket to `/api/voice/stream`.
- WebSocket `/api/voice/stream` — Twilio audio stream handler (handle `Connected`, `Start`, `Media`, `Stop` payloads; decode base64 mu-law audio).
- WebSocket `/ws/dashboard` — Dashboard/HITL websocket for real-time transcripts and escalation alerts.

Entities & DB notes

See `Voxora architecture` for the multi-tenant entities. Important DB details:
- MySQL is used; map JSON fields with `@Column(columnDefinition = "JSON")`.
- Entities: Organization, TwilioNumber, AiConfig (with JSON knowledge_base), CallLog.

Coding standards / operational notes

- Use `@Transactional` for DB writes (especially payment flow).
- Tag logs with `TwilioCallSid` for tracing. Use SLF4J.
- Graceful degradation: if Deepgram/ElevenLabs fail, return a TwiML fallback message explaining technical difficulties.
- Prefer Java Virtual Threads (Project Loom) for high-concurrency paths.

Next steps (where to start)

1. Implement `TwilioWebhookController` (`/api/voice/incoming`) per architecture doc.
2. Implement `TwilioAudioWebSocketHandler` to accept Twilio's WebSocket audio stream and forward to AI pipeline.
3. Implement `VoiceAiOrchestratorService` to integrate Deepgram (STT), Anthropic (LLM), ElevenLabs (TTS).
4. Add `PaymentAndNotificationService` to create Razorpay UPI links and send receipts via WhatsApp Business API.
5. Add unit/integration tests for each module and a minimal end-to-end smoke test.

Where to find architecture notes

Open the file at the repo root named `Voxora architecture` for a detailed step-by-step architecture and requirements.

Contact / ownership

This README is generated as a concise starter. If you'd like a more detailed developer README, module-by-module setup, or CI suggestions, tell me which area to expand.



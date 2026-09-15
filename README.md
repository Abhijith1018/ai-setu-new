# AI-Setu

**Real-time Hindi–English voice bridge for rural commerce.**

Two people who don't share a language talk on a normal phone call. AI-Setu sits in the middle — translating in real time, watching the conversation for manipulation or pressure tactics, and locking in deal terms before either side can walk it back.

🏆 **Grand Finalist, Top 6 in India** — AI for Bharat Hackathon (powered by AWS), selected from 95,000+ submissions.

---

## What it does

- A caller speaks in Hindi, the other side hears it in English (and vice versa) — near real time, over a standard PSTN phone call, no app required.
- While the call happens, an AI layer watches for **sentiment shifts and manipulation patterns**, extracts what's actually being agreed to, and requires **dual confirmation** before anything is treated as a locked deal.
- Built for the kind of transaction where one side is fluent in the deal and the other isn't — and where trust, not just translation, is the actual product.

## Architecture

![AI-Setu architecture diagram]
<img width="1127" height="592" alt="image" src="https://github.com/user-attachments/assets/5b2d6e95-e187-4799-a77f-9b6179b5d7a3" />


**Call flow, end to end:**

1. **Call participants** — Speaker A and Speaker B connect over a standard PSTN voice call, one on each side of the language gap.
2. **Twilio Media Streams** — a bidirectional WebSocket (WSS) carries base64-encoded μ-law audio (8 kHz, mono, ~20ms frames) between the call and the backend.
3. **Spring Boot orchestrator** (Java 21, Spring Boot 3.2, modular monolith) — handles the WebSocket connection, decodes/encodes audio, tracks call SID + stream SID, and coordinates the pipeline via Spring async events.
4. **Speech + AI pipeline:**
   - **Deepgram Nova-2** — streaming STT, Hindi/English, 300ms endpointing
   - **Redis** — holds the last 5 conversation turns as rolling context
   - **Groq (Llama 3.3 70B)** — reasoning over the transcript, non-streaming JSON output, temperature 0.3
   - **ElevenLabs Multilingual v2** — full-response TTS, μ-law 8kHz output routed back into the call

**Trust layer, running alongside the pipeline:**

| Feature | What it does |
|---|---|
| Translation | Real-time language conversion between speakers |
| Sentiment / manipulation flag | Detects pressure tactics or emotional manipulation mid-call |
| Listener advice | Surfaces guidance to the disadvantaged party in real time |
| Deal extraction | Pulls structured terms out of unstructured conversation |
| Ledger lock | In-memory dual confirmation before terms count as agreed |
| WhatsApp sandbox | Sends a contract message summarizing what was agreed |

## Tech stack

- **Backend:** Java 21, Spring Boot 3.2, WebSockets
- **Telephony:** Twilio Media Streams
- **STT:** Deepgram Nova-2 (streaming)
- **LLM:** Groq — Llama 3.3 70B
- **TTS:** ElevenLabs Multilingual v2
- **State:** Redis (rolling conversation context)
- **Messaging:** WhatsApp (Twilio sandbox)
- **Payments (sandboxed):** Razorpay

## Honest current limitations

This is a working prototype, not a finished product. Being upfront about where it stands:

- Fixed A/B language routing (not yet dynamic per speaker)
- Global speaker-scoped state (not yet isolated per call at scale)
- LLM + TTS are currently **buffered, not streaming** — a deliberate tradeoff to ship a reliable working system first
- No barge-in / mark / clear support yet (a speaker can't interrupt mid-response)
- **No verified end-to-end latency benchmark yet** — the pipeline is designed for low latency (300ms STT endpointing) but the full round-trip hasn't been formally measured

Converting this into a properly benchmarked, low-latency streaming pipeline with barge-in support is the next milestone.

## Getting started

### Prerequisites

- Java 21
- MySQL
- A Redis instance (e.g. Upstash)
- API keys for Twilio, Deepgram, Groq, and ElevenLabs

### Environment variables

Create a `.env` (or `application.properties`) with your own credentials — **never commit real keys to the repo**:

```env
DB_URL=jdbc:mysql://localhost:3306/your_db?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

REDIS_HOST=your_redis_host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

DEEPGRAM_API_KEY=your_deepgram_api_key
GROQ_API_KEY=your_groq_api_key
ELEVENLABS_API_KEY=your_elevenlabs_api_key
ELEVENLABS_VOICE_ID=your_elevenlabs_voice_id

TWILIO_ACCOUNT_SID=your_twilio_account_sid
TWILIO_AUTH_TOKEN=your_twilio_auth_token

RAZORPAY_KEY_ID=your_razorpay_key_id
RAZORPAY_KEY_SECRET=your_razorpay_key_secret
```

### Run

```bash
./mvnw spring-boot:run
```

Point a Twilio phone number's voice webhook at `POST /api/voice/incoming` to start routing calls through the pipeline.

## Roadmap

- [ ] Streaming LLM + TTS (replace buffered response with token-level streaming)
- [ ] Verified end-to-end latency benchmark
- [ ] Barge-in / interrupt support
- [ ] Per-call state isolation (move off global speaker-scoped state)
- [ ] Dynamic language detection instead of fixed A/B routing

## Results

🏆 Grand Finalist, Top 6 in India — AI for Bharat Hackathon powered by AWS (95,000+ submissions)

---

*Built solo — architecture, pipeline, and trust layer.*

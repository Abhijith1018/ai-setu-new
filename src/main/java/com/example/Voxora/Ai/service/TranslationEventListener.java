package com.example.Voxora.Ai.service;

import com.example.Voxora.Ai.event.TranslationRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.Voxora.Ai.websocket.TwilioAudioWebSocketHandler;

/**
 * Listens for TranslationRequestEvent and orchestrates the full pipeline:
 *
 * 1. Send transcribed text to Groq for agentic translation + analysis
 * 2. Extract translated_text from Groq's JSON response
 * 3. Check sentiment_shield_flag and log AI Whisperer advice
 * 4. Log Ledger Lock data (price/quantity) if present
 * 5. Send translated_text to ElevenLabs for TTS
 * 6. Route synthesized audio to the target speaker via Twilio WebSocket
 */
@Service
public class TranslationEventListener {

    private static final Logger log = LoggerFactory.getLogger(TranslationEventListener.class);

    private final GroqLlmService groqLlmService;
    private final ElevenLabsService elevenLabsService;
    private final TwilioAudioWebSocketHandler twilioAudioWebSocketHandler;
    private final ObjectMapper objectMapper;

    public TranslationEventListener(GroqLlmService groqLlmService,
                                    ElevenLabsService elevenLabsService,
                                    TwilioAudioWebSocketHandler twilioAudioWebSocketHandler,
                                    ObjectMapper objectMapper) {
        this.groqLlmService = groqLlmService;
        this.elevenLabsService = elevenLabsService;
        this.twilioAudioWebSocketHandler = twilioAudioWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    public void handleTranslationRequestEvent(TranslationRequestEvent event) {
        log.info("[ROUTER] Received Translation Event. Source: {} → Target: {}, Text: '{}'",
                 event.getSourceSpeakerId(), event.getTargetSpeakerId(), event.getOriginalText());

        // Print loudly to console for easy debugging
        String sourceLang = "A".equals(event.getSourceSpeakerId()) ? "Hindi" : "English";
        System.out.println("\n=======================================================");
        System.out.println("🗣️ STT (DEEPGRAM) Speaker " + event.getSourceSpeakerId()
                + " (" + sourceLang + "): " + event.getOriginalText());
        System.out.println("=======================================================\n");

        try {
            // ─── 1. GROQ: Agentic Translation + Sentiment + Ledger ───────────
            String groqJsonResponse = groqLlmService.processTranslation(event.getOriginalText()).join();

            if (groqJsonResponse == null || groqJsonResponse.isBlank()) {
                log.error("[ROUTER][GROQ] Empty response from Groq. Skipping pipeline.");
                return;
            }

            log.info("[ROUTER][GROQ] Raw JSON: {}", groqJsonResponse);

            // ─── 2. Parse the Groq JSON response ─────────────────────────────
            JsonNode groqJson = objectMapper.readTree(groqJsonResponse);

            String translatedText = groqJson.path("translated_text").asText("").trim();
            boolean sentimentFlag = groqJson.path("sentiment_shield_flag").asBoolean(false);
            String whispererAdvice = groqJson.path("ai_whisperer_advice").asText("");
            JsonNode ledgerLock = groqJson.path("ledger_lock");

            // ─── 3. Sentiment Shield ─────────────────────────────────────────
            if (sentimentFlag) {
                System.out.println("\n🚨🚨🚨 SENTIMENT SHIELD ALERT 🚨🚨🚨");
                System.out.println("⚠️  Manipulative tactics detected from Speaker " + event.getSourceSpeakerId());
                System.out.println("💡 AI Whisperer Advice: " + whispererAdvice);
                System.out.println("🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨\n");
                log.warn("[ROUTER][SENTIMENT] ⚠️ FLAG from Speaker {}. Advice: {}",
                         event.getSourceSpeakerId(), whispererAdvice);
            }

            // ─── 4. Ledger Lock ──────────────────────────────────────────────
            if (ledgerLock != null && !ledgerLock.isMissingNode()) {
                JsonNode priceNode = ledgerLock.path("price");
                JsonNode quantityNode = ledgerLock.path("quantity");
                String currency = ledgerLock.path("currency").asText("N/A");

                if (!priceNode.isNull() || !quantityNode.isNull()) {
                    System.out.println("\n📒 LEDGER LOCK DETECTED:");
                    System.out.println("   💰 Price: " + priceNode.asText("N/A"));
                    System.out.println("   📦 Quantity: " + quantityNode.asText("N/A"));
                    System.out.println("   💱 Currency: " + currency + "\n");
                    log.info("[ROUTER][LEDGER] Price={}, Quantity={}, Currency={}",
                             priceNode.asText("N/A"), quantityNode.asText("N/A"), currency);
                }
            }

            // ─── 5. ElevenLabs TTS ───────────────────────────────────────────
            if (translatedText.isBlank()) {
                log.warn("[ROUTER][GROQ] translated_text is blank. Skipping TTS.");
                return;
            }

            String targetLang = "A".equals(event.getTargetSpeakerId()) ? "Hindi" : "English";
            System.out.println("🔄 TRANSLATION: " + translatedText + " → Speaker "
                    + event.getTargetSpeakerId() + " (" + targetLang + ")");

            byte[] audioBytes = elevenLabsService.synthesizeAudio(translatedText).join();

            log.info("[ROUTER][ELEVENLABS] Received {} audio bytes for target Speaker {} ({})",
                     audioBytes != null ? audioBytes.length : 0,
                     event.getTargetSpeakerId(), targetLang);

            // ─── 6. Route to target speaker via Twilio WebSocket ─────────────
            if (audioBytes != null && audioBytes.length > 0) {
                twilioAudioWebSocketHandler.sendAudioToSpeaker(event.getTargetSpeakerId(), audioBytes);
                log.info("[ROUTER][DISPATCH] ✅ Dispatched translated audio: {} ({}) → {} ({})",
                         event.getSourceSpeakerId(), sourceLang,
                         event.getTargetSpeakerId(), targetLang);
            }

        } catch (Exception e) {
            log.error("[ROUTER] Error processing translation event.", e);
        }
    }
}

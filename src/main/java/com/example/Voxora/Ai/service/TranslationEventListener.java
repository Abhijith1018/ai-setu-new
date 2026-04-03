package com.example.Voxora.Ai.service;

import com.example.Voxora.Ai.event.TranslationRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.Voxora.Ai.service.GeminiLlmService;
import com.example.Voxora.Ai.service.ElevenLabsService;
import com.example.Voxora.Ai.websocket.TwilioAudioWebSocketHandler;

@Service
public class TranslationEventListener {

    private static final Logger log = LoggerFactory.getLogger(TranslationEventListener.class);

    private final GeminiLlmService geminiLlmService;
    private final ElevenLabsService elevenLabsService;
    private final TwilioAudioWebSocketHandler twilioAudioWebSocketHandler;

    public TranslationEventListener(GeminiLlmService geminiLlmService, ElevenLabsService elevenLabsService, TwilioAudioWebSocketHandler twilioAudioWebSocketHandler) {
        this.geminiLlmService = geminiLlmService;
        this.elevenLabsService = elevenLabsService;
        this.twilioAudioWebSocketHandler = twilioAudioWebSocketHandler;
    }

    @Async
    @EventListener
    public void handleTranslationRequestEvent(TranslationRequestEvent event) {
        log.info("[ROUTER] Received Translation Event. Source: {}, Target: {}, Text: {}",
                 event.getSourceSpeakerId(), event.getTargetSpeakerId(), event.getOriginalText());

        // Print loudly to console for easy debugging
        System.out.println("\n=======================================================");
        System.out.println("🗣️ STT (DEEPGRAM HEARD) from Speaker " + event.getSourceSpeakerId() + ": " + event.getOriginalText());
        System.out.println("=======================================================\n");

        try {
            // 1. Connect to Gemini with strict bidirectional translation logic
            String systemPrompt = "You are AI-Setu. If the input is Hindi, translate to English. " +
                                  "If English, translate to Hindi. Output ONLY the translated words. " +
                                  "No filler or conversational additives.";

            String translatedText = geminiLlmService.generateTextResponse(event.getOriginalText(), systemPrompt).join();

            log.info("[ROUTER][GEMINI] Processed Text: {}", translatedText);

            if (translatedText != null && !translatedText.isBlank()) {
                // 2. Send translated text to ElevenLabs for TTS (multilingual implied by service config typically, but handled here)
                byte[] audioBytes = elevenLabsService.synthesizeAudio(translatedText).join();

                log.info("[ROUTER][ELEVENLABS] Received Audio Bytes. length: {}", audioBytes != null ? audioBytes.length : 0);

                // 3 & 4. Cross-stream injection: Route audioBytes to the correct targetWebSocket session.
                if (audioBytes != null && audioBytes.length > 0) {
                    twilioAudioWebSocketHandler.sendAudioToSpeaker(event.getTargetSpeakerId(), audioBytes);
                    log.info("[ROUTER][DISPATCH] Dispatched translated audio from {} to {}",
                             event.getSourceSpeakerId(), event.getTargetSpeakerId());
                }
            }

        } catch (Exception e) {
            log.error("[ROUTER] Error processing translation event.", e);
        }
    }
}

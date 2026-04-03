package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.DeepgramService;
import com.example.Voxora.Ai.service.GeminiLlmService;
import com.example.Voxora.Ai.service.VoiceAiOrchestratorService;
import com.example.Voxora.Ai.event.TranslationRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class VoiceAiOrchestratorServiceImpl implements VoiceAiOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAiOrchestratorServiceImpl.class);

    private final DeepgramService deepgramService;
    private final ApplicationEventPublisher eventPublisher;

    // Buffer for speaker audio chunks
    private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();

    public VoiceAiOrchestratorServiceImpl(DeepgramService deepgramService,
                                          ApplicationEventPublisher eventPublisher) {
        this.deepgramService = deepgramService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onConnected(String sessionId) {
        log.info("Twilio stream connected: sessionId={}", sessionId);
    }

    @Override
    public void onStart(String sessionId, String streamSid, String callSid) {
        log.info("Twilio stream started: sessionId={}, streamSid={}, callSid={}", sessionId, streamSid, callSid);
        audioBuffers.put(sessionId, new ByteArrayOutputStream());
    }

    @Override
    public void onMedia(String sessionId, String streamSid, byte[] audioBytes, String speakerId) {
        int inboundSize = audioBytes == null ? 0 : audioBytes.length;
        log.info("[PIPELINE][RECEIVE] Raw audio bytes received from Twilio: sessionId={}, streamSid={}, speakerId={}, bytes={}",
                sessionId, streamSid, speakerId, inboundSize);

        if (inboundSize == 0 || "UNKNOWN".equals(speakerId)) {
            log.warn("[PIPELINE][RECEIVE] Empty audio mapping or unknown speaker; skipping pipeline. sessionId={}, streamSid={}, speakerId={}",
                    sessionId, streamSid, speakerId);
            return;
        }

        // Buffer the 160 byte chunks (8000 bytes = 1 second of mu-law audio)
        ByteArrayOutputStream buffer = audioBuffers.computeIfAbsent(sessionId, k -> new ByteArrayOutputStream());
        try {
            synchronized (buffer) {
                buffer.write(audioBytes);
            }
        } catch (Exception e) {
            log.error("Failed to write to buffer", e);
        }

        // Wait until we have 5 seconds of audio (40000 bytes)
        if (buffer.size() >= 40000) {
            byte[] chunkToSend;
            synchronized (buffer) {
                chunkToSend = buffer.toByteArray();
                buffer.reset(); // clear buffer for the next chunk
            }

            try {
                log.info("[PIPELINE][BUFFER] Sending {} bytes to Deepgram for speaker {}", chunkToSend.length, speakerId);
                deepgramService.transcribeAudio(chunkToSend)
                    .thenAccept(transcript -> {
                        String safeTranscript = transcript == null ? "" : transcript.trim();
                        log.info("[PIPELINE][DEEPGRAM] STT text received: sessionId={}, streamSid={}, speakerId={}, text='{}'",
                                sessionId, streamSid, speakerId, safeTranscript);

                        if (!safeTranscript.isBlank()) {
                            String targetSpeakerId = "A".equals(speakerId) ? "B" : "A";
                            TranslationRequestEvent event = new TranslationRequestEvent(speakerId, targetSpeakerId, safeTranscript);
                            eventPublisher.publishEvent(event);
                            log.info("[PIPELINE][ROUTER] Fired TranslationRequestEvent for text: {}", safeTranscript);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("[PIPELINE][DEEPGRAM] STT failed. sessionId={}, streamSid={}, error={}",
                                  sessionId, streamSid, e.getMessage(), e);
                        return null;
                    });
            } catch (Exception e) {
                log.error("[PIPELINE][DEEPGRAM] Failed to invoke STT call. sessionId={}, streamSid={}, error={}",
                        sessionId, streamSid, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStop(String sessionId, String streamSid) {
        log.info("Twilio stream stopped: sessionId={}, streamSid={}", sessionId, streamSid);
        audioBuffers.remove(sessionId);
    }
}

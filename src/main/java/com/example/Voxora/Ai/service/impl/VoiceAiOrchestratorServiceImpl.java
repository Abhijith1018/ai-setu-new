package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.DeepgramService;
import com.example.Voxora.Ai.service.ElevenLabsService;
import com.example.Voxora.Ai.service.GeminiLlmService;
import com.example.Voxora.Ai.service.VoiceAiOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class VoiceAiOrchestratorServiceImpl implements VoiceAiOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAiOrchestratorServiceImpl.class);
    private static final String DEFAULT_SYSTEM_PROMPT = "You are Voxora voice assistant. Keep responses short and natural for phone calls.";

    private final DeepgramService deepgramService;
    private final GeminiLlmService geminiLlmService;
    private final ElevenLabsService elevenLabsService;

    public VoiceAiOrchestratorServiceImpl(DeepgramService deepgramService,
                                          GeminiLlmService geminiLlmService,
                                          ElevenLabsService elevenLabsService) {
        this.deepgramService = deepgramService;
        this.geminiLlmService = geminiLlmService;
        this.elevenLabsService = elevenLabsService;
    }

    @Override
    public void onConnected(String sessionId) {
        log.info("Twilio stream connected: sessionId={}", sessionId);
    }

    @Override
    public void onStart(String sessionId, String streamSid, String callSid) {
        log.info("Twilio stream started: sessionId={}, streamSid={}, callSid={}", sessionId, streamSid, callSid);
    }

    @Override
    public CompletableFuture<byte[]> onMedia(String sessionId, String streamSid, byte[] audioBytes) {
        int inboundSize = audioBytes == null ? 0 : audioBytes.length;
        log.info("[PIPELINE][RECEIVE] Raw audio bytes received from Twilio: sessionId={}, streamSid={}, bytes={}",
                sessionId, streamSid, inboundSize);

        if (inboundSize == 0) {
            log.warn("[PIPELINE][RECEIVE] Empty audio payload received; skipping pipeline. sessionId={}, streamSid={}",
                    sessionId, streamSid);
            return CompletableFuture.completedFuture(new byte[0]);
        }

        CompletableFuture<String> sttFuture;
        try {
            sttFuture = deepgramService.transcribeAudio(audioBytes);
        } catch (Exception e) {
            log.error("[PIPELINE][DEEPGRAM] Failed to invoke STT call. sessionId={}, streamSid={}, error={}",
                    sessionId, streamSid, e.getMessage(), e);
            return CompletableFuture.completedFuture(new byte[0]);
        }

        return sttFuture
                .thenCompose(transcript -> {
                    String safeTranscript = transcript == null ? "" : transcript.trim();
                    log.info("[PIPELINE][DEEPGRAM] STT text received: sessionId={}, streamSid={}, text='{}'",
                            sessionId, streamSid, safeTranscript);

                    if (safeTranscript.isBlank()) {
                        log.warn("[PIPELINE][DEEPGRAM] Empty transcript returned; skipping LLM/TTS. sessionId={}, streamSid={}",
                                sessionId, streamSid);
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        return geminiLlmService.generateTextResponse(safeTranscript, DEFAULT_SYSTEM_PROMPT);
                    } catch (Exception e) {
                        log.error("[PIPELINE][GEMINI] Failed to invoke LLM call. sessionId={}, streamSid={}, error={}",
                                sessionId, streamSid, e.getMessage(), e);
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .thenCompose(geminiText -> {
                    if (geminiText == null) {
                        return CompletableFuture.completedFuture(new byte[0]);
                    }

                    String safeGeminiText = geminiText.trim();
                    log.info("[PIPELINE][GEMINI] LLM text received: sessionId={}, streamSid={}, text='{}'",
                            sessionId, streamSid, safeGeminiText);

                    if (safeGeminiText.isBlank()) {
                        log.warn("[PIPELINE][GEMINI] Empty LLM response; skipping TTS. sessionId={}, streamSid={}",
                                sessionId, streamSid);
                        return CompletableFuture.completedFuture(new byte[0]);
                    }

                    try {
                        return elevenLabsService.synthesizeAudio(safeGeminiText);
                    } catch (Exception e) {
                        log.error("[PIPELINE][ELEVENLABS] Failed to invoke TTS call. sessionId={}, streamSid={}, error={}",
                                sessionId, streamSid, e.getMessage(), e);
                        return CompletableFuture.completedFuture(new byte[0]);
                    }
                })
                .thenApply(ttsBytes -> {
                    int ttsSize = ttsBytes == null ? 0 : ttsBytes.length;
                    log.info("[PIPELINE][ELEVENLABS] TTS audio bytes received: sessionId={}, streamSid={}, bytes={}",
                            sessionId, streamSid, ttsSize);
                    return ttsBytes == null ? new byte[0] : ttsBytes;
                })
                .exceptionally(e -> {
                    log.error("[PIPELINE] Pipeline failed unexpectedly. sessionId={}, streamSid={}, error={}",
                            sessionId, streamSid, e.getMessage(), e);
                    return new byte[0];
                });
    }

    @Override
    public void onStop(String sessionId, String streamSid) {
        log.info("Twilio stream stopped: sessionId={}, streamSid={}", sessionId, streamSid);
    }
}

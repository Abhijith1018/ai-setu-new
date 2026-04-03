package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.ElevenLabsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Service
public class ElevenLabsServiceImpl implements ElevenLabsService {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsServiceImpl.class);

    private final WebClient webClient;
    private final String defaultVoiceId;

    public ElevenLabsServiceImpl(WebClient.Builder webClientBuilder,
                                 @Value("${voxora.api.elevenlabs.key:}") String apiKey,
                                 @Value("${voxora.api.elevenlabs.voiceId:}") String defaultVoiceId) {
        this.defaultVoiceId = defaultVoiceId;

        log.info("Initialized ElevenLabs with Voice ID {} and API Key safely loaded from .env (Starts with: {})",
                 defaultVoiceId, apiKey != null && apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "INVALID_KEY");

        this.webClient = webClientBuilder
                .baseUrl("https://api.elevenlabs.io/v1")
                .defaultHeader("xi-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public CompletableFuture<byte[]> synthesizeAudio(String text) {
        log.debug("Sending text for TTS synthesis. Length: {}", text.length());

        String jsonBody = "{\"text\": \"" + text + "\", "
                        + "\"model_id\": \"eleven_multilingual_v2\"}";

        return webClient.post()
                .uri("/text-to-speech/" + defaultVoiceId + "/stream?output_format=ulaw_8000") // for Twilio 8khz mulaw direct streaming if possible
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(byte[].class)
                .toFuture()
                .thenApply(audioResponse -> {
                    log.debug("Received {} synthesized audio bytes", audioResponse.length);
                    return audioResponse;
                })
                .exceptionally(ex -> {
                    log.error("TTS synthesis failed: {}", ex.getMessage());
                    return new byte[0]; // fallback silent payload
                });
    }
}

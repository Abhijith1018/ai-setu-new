package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.DeepgramService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Service
public class DeepgramServiceImpl implements DeepgramService {

    private static final Logger log = LoggerFactory.getLogger(DeepgramServiceImpl.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepgramServiceImpl(WebClient.Builder webClientBuilder,
                               @Value("${voxora.api.deepgram.key:}") String apiKey,
                               ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Deepgram's API requires `Token ` prefix for API keys
        String authHeaderValue = "Token " + apiKey;

        log.info("Initialized Deepgram with API Key safely loaded from .env (Starts with: {})",
                apiKey != null && apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "INVALID_KEY");

        this.webClient = webClientBuilder
                .baseUrl("https://api.deepgram.com/v1")
                .defaultHeader("Authorization", authHeaderValue)
                .defaultHeader("Content-Type", "audio/mulaw") // Twilio 8khz mulaw stream
                .build();
    }

    @Override
    public CompletableFuture<String> transcribeAudio(byte[] audioBytes) {
        log.debug("Sending {} bytes to Deepgram STT", audioBytes.length);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/listen")
                        .queryParam("model", "nova-2")
                        .queryParam("encoding", "mulaw")
                        .queryParam("sample_rate", "8000")
                        .queryParam("channels", "1")
                        .build())
                .bodyValue(audioBytes)
                .retrieve()
                .bodyToMono(String.class)
                .toFuture()
                .thenApply(responseJson -> {
                    try {
                        JsonNode root = objectMapper.readTree(responseJson);
                        JsonNode transcriptNode = root.path("results")
                                .path("channels")
                                .path(0)
                                .path("alternatives")
                                .path(0)
                                .path("transcript");
                        String transcript = transcriptNode.asText("").trim();
                        log.debug("Received STT response from Deepgram. transcript='{}'", transcript);
                        return transcript;
                    } catch (Exception parseEx) {
                        log.error("Failed to parse Deepgram response: {}", parseEx.getMessage());
                        return "";
                    }
                })
                .exceptionally(ex -> {
                    log.error("Deepgram transcription failed: {}", ex.getMessage());
                    return "";
                });
    }
}

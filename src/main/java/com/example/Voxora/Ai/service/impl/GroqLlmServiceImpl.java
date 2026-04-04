package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.GroqLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Groq LLM service implementation using the OpenAI-compatible API.
 *
 * Handles:
 * 1. Bidirectional translation (Hindi ↔ English)
 * 2. Sentiment Shield — detects aggressive/manipulative negotiation tactics
 * 3. AI Whisperer — generates tactical advice for the listener
 * 4. Ledger Lock — extracts agreed price/quantity from conversation
 *
 * Returns structured JSON for downstream processing.
 */
@Service
public class GroqLlmServiceImpl implements GroqLlmService {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
            You are AI-Setu, an intelligent, real-time bilingual translator and negotiation assistant. \
            Your primary task is to translate the input accurately between Hindi and English.

            Simultaneously, you must analyze the input to execute three core functions:
            1. SENTIMENT SHIELD: Detect if the speaker is using aggressive language, intimidation, \
            predatory pricing, or manipulative negotiation tactics.
            2. AI WHISPERER: If manipulative tactics are detected, or if the user needs guidance, \
            generate a short, tactical piece of advice to protect the listener.
            3. LEDGER LOCK: If the conversation contains a clear agreement or discussion regarding \
            a specific price and quantity, extract those exact values.

            You MUST respond STRICTLY in valid JSON format. Do not include markdown code blocks. \
            Use this exact structure:
            {
              "translated_text": "<translated text here>",
              "sentiment_shield_flag": <true or false>,
              "ai_whisperer_advice": "<tactical advice or empty string>",
              "ledger_lock": {
                "price": <number or null>,
                "quantity": <number or null>,
                "currency": "<currency code or null>"
              }
            }
            """;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GroqLlmServiceImpl(WebClient.Builder webClientBuilder,
                              @Value("${voxora.api.groq.key:}") String apiKey,
                              ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        log.info("[GROQ] Initialized with API Key (starts with: {})",
                apiKey != null && apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "INVALID_KEY");

        this.webClient = webClientBuilder
                .baseUrl("https://api.groq.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public CompletableFuture<String> processTranslation(String text) {
        log.info("[GROQ] Sending text for agentic processing. Input length: {}", text.length());

        // Build OpenAI-compatible request body
        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)
                ),
                "temperature", 0.3,
                "max_tokens", 1024,
                "response_format", Map.of("type", "json_object")
        );

        return webClient.post()
                .uri("/openai/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .toFuture()
                .thenApply(responseJson -> {
                    try {
                        JsonNode root = objectMapper.readTree(responseJson);
                        JsonNode choices = root.path("choices");

                        if (choices.isArray() && !choices.isEmpty()) {
                            String content = choices.get(0)
                                    .path("message")
                                    .path("content")
                                    .asText("");

                            log.info("[GROQ] Raw response content: {}", content);
                            return content;
                        }

                        log.warn("[GROQ] Unexpected response format: {}", responseJson);
                        return "";
                    } catch (Exception e) {
                        log.error("[GROQ] Failed to parse response: {}", e.getMessage(), e);
                        return "";
                    }
                })
                .exceptionally(ex -> {
                    log.error("[GROQ] API request failed: {}", ex.getMessage(), ex);
                    return "";
                });
    }
}

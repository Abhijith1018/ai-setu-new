package com.example.Voxora.Ai.service;

import java.util.concurrent.CompletableFuture;

public interface GeminiLlmService {
    CompletableFuture<String> generateTextResponse(String text, String systemPrompt);
}


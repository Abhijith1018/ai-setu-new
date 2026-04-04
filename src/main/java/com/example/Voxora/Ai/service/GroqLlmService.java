package com.example.Voxora.Ai.service;

import java.util.concurrent.CompletableFuture;

/**
 * Groq LLM service for agentic translation, sentiment analysis, and ledger extraction.
 * Replaces GeminiLlmService for the AI-Setu pipeline.
 */
public interface GroqLlmService {

    /**
     * Sends transcribed text to Groq for agentic processing.
     * Returns a raw JSON string containing:
     *   - translated_text: the translated text (Hindi↔English)
     *   - sentiment_shield_flag: boolean indicating manipulative tactics
     *   - ai_whisperer_advice: tactical advice string (if applicable)
     *   - ledger_lock: { price, quantity, currency } (if applicable)
     *
     * @param text the transcribed text from Deepgram
     * @return CompletableFuture resolving to the raw JSON response content from Groq
     */
    CompletableFuture<String> processTranslation(String text);
}

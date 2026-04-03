package com.example.Voxora.Ai.service;

import java.util.concurrent.CompletableFuture;

public interface DeepgramService {
    /**
     * Sends audio bytes to Deepgram STT and receives transcribed text asynchronously.
     * @param audioBytes Base64 or raw mu-law audio
     * @return CompletableFuture completing with transcribed text
     */
    CompletableFuture<String> transcribeAudio(byte[] audioBytes);
}

package com.example.Voxora.Ai.service;

import java.util.concurrent.CompletableFuture;

public interface VoiceAiOrchestratorService {

    void onConnected(String sessionId);

    void onStart(String sessionId, String streamSid, String callSid);

    CompletableFuture<byte[]> onMedia(String sessionId, String streamSid, byte[] audioBytes);

    void onStop(String sessionId, String streamSid);
}

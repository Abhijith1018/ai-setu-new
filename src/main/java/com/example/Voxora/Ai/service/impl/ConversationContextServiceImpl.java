package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.ConversationContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis LIST-backed sliding window of the last 5 conversation turns per speaker.
 *
 * Keys:   convo:A:turns → LIST of strings
 *         convo:B:turns → LIST of strings
 * Pattern: RPUSH + LTRIM(0, 4) keeps only the last 5 entries.
 *
 * Output format matches user's mental model:
 *   A_1: कितने का गेहूं है?
 *   A_2: ज्यादा है, कम करो
 */
@Service
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextServiceImpl.class);
    private static final int MAX_TURNS = 5;

    private final StringRedisTemplate redisTemplate;

    public ConversationContextServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String redisKey(String speakerId) {
        return "convo:" + speakerId + ":turns";
    }

    @Override
    public void addTurn(String speakerId, String text) {
        String key = redisKey(speakerId);
        try {
            redisTemplate.opsForList().rightPush(key, text);
            redisTemplate.opsForList().trim(key, -MAX_TURNS, -1); // keep only last 5
            log.debug("[REDIS] Stored turn for speaker {}: '{}'", speakerId, text);
        } catch (Exception e) {
            log.error("[REDIS] Failed to store turn for speaker {}: {}", speakerId, e.getMessage(), e);
        }
    }

    @Override
    public String getContextBlock(String speakerId) {
        String key = redisKey(speakerId);
        try {
            List<String> turns = redisTemplate.opsForList().range(key, 0, -1);
            if (turns == null || turns.isEmpty()) {
                return "Speaker " + speakerId + ": (no previous turns)";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < turns.size(); i++) {
                sb.append(speakerId).append("_").append(i + 1).append(": ").append(turns.get(i));
                if (i < turns.size() - 1) sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[REDIS] Failed to read context for speaker {}: {}", speakerId, e.getMessage(), e);
            return "Speaker " + speakerId + ": (context unavailable)";
        }
    }

    @Override
    public String getFullContextForGroq() {
        String contextA = getContextBlock("A");
        String contextB = getContextBlock("B");

        return "=== SPEAKER A (Hindi) PREVIOUS TURNS ===\n" + contextA +
               "\n\n=== SPEAKER B (English) PREVIOUS TURNS ===\n" + contextB;
    }

    @Override
    public void clearAll() {
        try {
            redisTemplate.delete(redisKey("A"));
            redisTemplate.delete(redisKey("B"));
            log.info("[REDIS] Cleared all conversation context");
        } catch (Exception e) {
            log.error("[REDIS] Failed to clear context: {}", e.getMessage(), e);
        }
    }
}

package com.example.Voxora.Ai.service;

import com.example.Voxora.Ai.entity.CallLog;

public interface PaymentAndNotificationService {
    void processBooking(CallLog callLog, String jsonExtractedFromClaude);
}


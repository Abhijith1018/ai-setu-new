package com.example.Voxora.Ai.controller;

import com.example.Voxora.Ai.entity.Organization;
import com.example.Voxora.Ai.repository.TwilioNumberRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/voice")
public class TwilioWebhookController {

    @Value("${TWILIO_ACCOUNT_SID}")
    private String accountSid;

    @Value("${TWILIO_AUTH_TOKEN}")
    private String authToken;

    // ✅ UPDATED: Your live Ngrok WebSocket URL is injected here
    private static final String STREAM_TWIML =
            "<Response><Connect><Stream url=\"wss://ungushing-tabatha-unprimly.ngrok-free.dev/api/voice/stream\" /></Connect></Response>";

    private static final String NOT_RECOGNIZED_TWIML =
            "<Response><Say>Number not recognized</Say></Response>";

    private final TwilioNumberRepository twilioNumberRepository;

    public TwilioWebhookController(TwilioNumberRepository twilioNumberRepository) {
        this.twilioNumberRepository = twilioNumberRepository;
    }

    @PostMapping(value = "/incoming", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> incomingCall(@RequestParam("To") String toNumber) {
        String normalizedToNumber = toNumber == null ? "" : toNumber.trim();

        // Checks if the Twilio number you bought exists in your MySQL database
        boolean organizationFound = twilioNumberRepository.findByPhoneNumber(normalizedToNumber)
                .map(twilioNumber -> twilioNumber.getOrganization())
                .map(Organization::getId)
                .isPresent();

        String twiml = organizationFound ? STREAM_TWIML : NOT_RECOGNIZED_TWIML;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(twiml);
    }

    @GetMapping("/start-outbound")
    public ResponseEntity<String> startOutboundCall() {
        Twilio.init(accountSid, authToken);

        String twimlString = "<Response><Connect><Stream url=\"wss://ungushing-tabatha-unprimly.ngrok-free.dev/api/voice/stream\" /></Connect></Response>";

        Call call = Call.creator(
                        new com.twilio.type.PhoneNumber("+917207658298"),
                        new com.twilio.type.PhoneNumber("+12603083509"),
                        new com.twilio.type.Twiml(twimlString))
                .create();

        return ResponseEntity.ok("Outbound Twilio call started successfully. Call SID: " + call.getSid());
    }
}
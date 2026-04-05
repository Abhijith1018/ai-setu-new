package com.example.Voxora.Ai.service.impl;

import com.example.Voxora.Ai.service.LiveMarketPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardcoded commodity market prices for MVP.
 * Replace with actual live API (e.g., Agmarknet, data.gov.in) later.
 */
@Service
public class LiveMarketPriceServiceImpl implements LiveMarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketPriceServiceImpl.class);

    private static final Map<String, String> MARKET_PRICES = new ConcurrentHashMap<>(Map.of(
            "wheat",   "₹2,800 per quintal",
            "rice",    "₹3,200 per quintal",
            "cotton",  "₹7,500 per quintal",
            "soybean", "₹4,600 per quintal",
            "maize",   "₹2,200 per quintal",
            "sugar",   "₹3,600 per quintal",
            "mustard", "₹5,400 per quintal",
            "chana",   "₹5,200 per quintal",
            "bajra",   "₹2,500 per quintal",
            "jowar",   "₹3,100 per quintal"
    ));

    @Override
    public String getMarketPrice(String commodity) {
        if (commodity == null || commodity.isBlank()) return null;

        String normalized = commodity.toLowerCase().trim();
        String price = MARKET_PRICES.get(normalized);

        if (price != null) {
            log.info("[MARKET] Lookup for '{}': {}", normalized, price);
        } else {
            log.warn("[MARKET] No price data for commodity: '{}'", normalized);
        }
        return price;
    }
}

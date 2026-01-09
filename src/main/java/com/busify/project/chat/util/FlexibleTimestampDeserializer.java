package com.busify.project.chat.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Custom deserializer để xử lý timestamp linh hoạt:
 * - Nhận Long (milliseconds): 1736406696745
 * - Nhận String ISO: "2026-01-09T13:51:36.745"
 * - Nhận String ISO với timezone: "2026-01-09T13:51:36.745+07:00"
 */
@Slf4j
public class FlexibleTimestampDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        
        if (value == null || value.trim().isEmpty()) {
            // Nếu null hoặc empty, trả về thời gian hiện tại
            return System.currentTimeMillis();
        }

        try {
            // Case 1: Nhận Long trực tiếp (1736406696745)
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // Case 2: Nhận String ISO format
            try {
                // Parse ISO format với timezone
                Instant instant = Instant.parse(value);
                long millis = instant.toEpochMilli();
                log.debug("Parsed ISO timestamp '{}' to {}", value, millis);
                return millis;
            } catch (DateTimeParseException ex1) {
                try {
                    // Parse ISO format without timezone (2026-01-09T13:51:36.745)
                    LocalDateTime dateTime = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    long millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    log.debug("Parsed local datetime '{}' to {}", value, millis);
                    return millis;
                } catch (DateTimeParseException ex2) {
                    // Fallback: Return current time
                    log.warn("Unable to parse timestamp '{}', using current time", value);
                    return System.currentTimeMillis();
                }
            }
        }
    }
}

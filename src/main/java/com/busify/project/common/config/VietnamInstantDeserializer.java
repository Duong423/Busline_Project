package com.busify.project.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Custom deserializer để parse datetime string từ frontend thành Instant
 * 
 * Logic: Giữ nguyên giá trị giờ người dùng nhập, lưu vào DB đúng giờ đó.
 * VD: Người dùng nhập 13:00 → DB lưu 13:00:00 (không convert timezone)
 * 
 * Cách thực hiện: Parse LocalDateTime rồi convert sang Instant với UTC offset
 * để khi Hibernate lưu vào MySQL (với timezone VN), nó sẽ hiển thị đúng giờ.
 */
public class VietnamInstantDeserializer extends JsonDeserializer<Instant> {
    
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    
    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Xử lý nếu là số (epoch millis)
        if (p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
            return Instant.ofEpochMilli(p.getLongValue());
        }
        
        String value = p.getText();
        
        if (value == null || value.trim().isEmpty() || value.equals("null")) {
            return null;
        }
        
        value = value.trim();
        
        System.out.println("[VietnamInstantDeserializer] Parsing value: " + value);
        
        // Lấy LocalDateTime từ input (bỏ qua mọi timezone info)
        LocalDateTime localDateTime = extractLocalDateTime(value);
        
        if (localDateTime == null) {
            throw new IllegalArgumentException(
                "Không thể parse datetime: " + value + 
                ". Vui lòng sử dụng format: yyyy-MM-dd'T'HH:mm:ss"
            );
        }
        
        // Convert LocalDateTime sang Instant với UTC offset
        // Để khi Hibernate lưu với timezone VN, DB sẽ hiển thị đúng giờ này
        Instant result = localDateTime.toInstant(ZoneOffset.UTC);
        
        System.out.println("[VietnamInstantDeserializer] LocalDateTime: " + localDateTime);
        System.out.println("[VietnamInstantDeserializer] Result Instant: " + result);
        
        return result;
    }
    
    private LocalDateTime extractLocalDateTime(String value) {
        // Nếu kết thúc bằng Z, bỏ Z
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1);
        }
        
        // Nếu có timezone offset như +07:00, lấy phần trước offset
        if (value.matches(".*[+-]\\d{2}:\\d{2}$")) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(value);
                return zdt.toLocalDateTime();
            } catch (DateTimeParseException e) {
                // Fallback: cắt bỏ phần offset
                value = value.replaceAll("[+-]\\d{2}:\\d{2}$", "");
            }
        }
        
        // Thử parse với các format khác nhau
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        
        // Nếu chỉ có ngày, thêm giờ 00:00:00
        try {
            return LocalDateTime.parse(value + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        
        return null;
    }
}

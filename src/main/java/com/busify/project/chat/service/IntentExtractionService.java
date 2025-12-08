package com.busify.project.chat.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.busify.project.chat.dto.SearchIntentDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service trích xuất ý định tìm kiếm từ câu chat sử dụng AI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntentExtractionService {

    private final OpenRouterService openRouterService;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;

    /**
     * Trích xuất ý định tìm kiếm từ tin nhắn của user
     */
    public SearchIntentDTO extractSearchIntent(String userMessage) {
        try {
            log.info("Extracting search intent from message: {}", userMessage);

            // Tạo prompt để trích xuất thông tin
            String systemPrompt = createIntentExtractionPrompt();
            
            List<OpenRouterService.Message> messages = List.of(
                new OpenRouterService.Message("system", systemPrompt),
                new OpenRouterService.Message("user", userMessage)
            );

            // Gọi AI để trích xuất
            log.info("🔑 Using OpenAI API key from config (length: {})", 
                openaiApiKey != null ? openaiApiKey.length() : 0);
            
            String aiResponse = openRouterService.getChatCompletion(
                openaiApiKey, // Sử dụng key từ application.properties
                "google/gemini-2.5-flash", // Google Gemini 2.5 Flash - Latest version
                messages,
                500,
                0.3 // Temperature thấp để chính xác hơn
            );

            if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                // Log raw AI response để debug
                log.info("🤖 AI Response (raw): {}", aiResponse);
                
                // Parse JSON response
                SearchIntentDTO intent = parseAIResponse(aiResponse, userMessage);
                log.info("✅ Extracted intent: {}", intent);
                return intent;
            } else {
                // Fallback: Dùng regex pattern matching
                log.warn("⚠️ AI returned empty, using regex fallback");
                return extractIntentWithRegex(userMessage);
            }

        } catch (Exception e) {
            log.error("Error extracting intent, using regex fallback", e);
            return extractIntentWithRegex(userMessage);
        }
    }

    /**
     * Tạo prompt cho AI để trích xuất thông tin
     */
    private String createIntentExtractionPrompt() {
        return """
            Bạn là trợ lý trích xuất thông tin đặt vé xe từ câu chat của khách hàng.
            
            Nhiệm vụ: Phân tích câu chat và trích xuất các thông tin sau (nếu có):
            - intentType: SEARCH_TRIP (tìm chuyến), BOOK_TICKET (đặt vé), ASK_PRICE (hỏi giá), ASK_SCHEDULE (hỏi lịch), GENERAL_QUESTION
            - departure: Điểm đi (tên địa điểm)
            - destination: Điểm đến (tên địa điểm)
            - departureDate: Ngày đi (format: yyyy-MM-dd)
            - returnDate: Ngày về (format: yyyy-MM-dd) - CHỈ có khi khách đặt vé khứ hồi
            - isRoundTrip: true nếu khách muốn đặt vé khứ hồi (2 chiều), false nếu chỉ đi 1 chiều
            - numberOfTickets: Số lượng vé
            - busType: Loại xe (VIP, thường, giường nằm)
            - priceMin: Giá tối thiểu
            - priceMax: Giá tối đa
            - confidence: Độ tin cậy (0.0-1.0)
            
            QUAN TRỌNG - Nhận biết VÉ KHỨ HỒI:
            - Các từ khóa khứ hồi: "khứ hồi", "2 chiều", "hai chiều", "đi về", "cả đi lẫn về", "về ngày", "ngày về"
            - Nếu có từ khóa khứ hồi -> isRoundTrip = true
            - Nếu có ngày về -> returnDate = ngày đó, isRoundTrip = true
            - Nếu không có từ khóa khứ hồi và không có ngày về -> isRoundTrip = false (hoặc không set)
            
            Danh sách địa điểm ở Việt Nam (tỉnh, thành phố, bến xe):
            Miền Bắc: Hà Nội, Giáp Bát, Mỹ Đình, Hải Phòng, Hạ Long, Ninh Bình, Sapa,
                      Hà Giang, Cao Bằng, Lào Cai, Điện Biên, Sơn La, Yên Bái,
            Miền Trung: Đà Nẵng, Huế, Nha Trang, Đà Lạt, Hội An, Quy Nhơn, Vũng Tàu,
                        Thanh Hóa, Vinh, Quảng Bình, Quảng Trị, Quảng Nam, Quảng Ngãi,
            Miền Nam: TP.HCM, Sài Gòn, Miền Đông, Miền Tây, Cần Thơ, An Giang, Kiên Giang,
                      Đồng Nai, Bình Dương, Vĩnh Long, Cà Mau
            
            Trả về CHỈ JSON object (không có text khác). Ví dụ:
            
            Input: "Cần Thơ đi Giáp Bát ngày 29-11"
            Output: {
                "intentType": "SEARCH_TRIP",
                "departure": "Cần Thơ",
                "destination": "Giáp Bát",
                "departureDate": "2025-11-29",
                "isRoundTrip": false,
                "confidence": 0.9
            }
            
            Input: "Tìm vé khứ hồi từ Hà Nội đi Đà Nẵng, đi ngày 15/12, về ngày 20/12"
            Output: {
                "intentType": "SEARCH_TRIP",
                "departure": "Hà Nội",
                "destination": "Đà Nẵng",
                "departureDate": "2025-12-15",
                "returnDate": "2025-12-20",
                "isRoundTrip": true,
                "confidence": 0.95
            }
            
            Input: "Đặt vé 2 chiều Sài Gòn - Đà Lạt ngày 10/12"
            Output: {
                "intentType": "BOOK_TICKET",
                "departure": "Sài Gòn",
                "destination": "Đà Lạt",
                "departureDate": "2025-12-10",
                "isRoundTrip": true,
                "confidence": 0.9
            }
            
            Input: "xe từ Hà Nội đi Đà Nẵng"
            Output: {
                "intentType": "SEARCH_TRIP",
                "departure": "Hà Nội",
                "destination": "Đà Nẵng",
                "isRoundTrip": false,
                "confidence": 0.8
            }
            
            Nếu không tìm thấy thông tin nào:
            {
                "intentType": "GENERAL_QUESTION",
                "confidence": 0.5
            }
            """;
    }

    /**
     * Parse response từ AI
     */
    private SearchIntentDTO parseAIResponse(String aiResponse, String originalMessage) {
        try {
            // Tìm JSON object trong response
            String jsonStr = extractJsonFromText(aiResponse);
            
            if (jsonStr != null) {
                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                
                SearchIntentDTO.SearchIntentDTOBuilder builder = SearchIntentDTO.builder()
                    .intentType(getStringValue(jsonNode, "intentType", "GENERAL_QUESTION"))
                    .departure(getStringValue(jsonNode, "departure", null))
                    .destination(getStringValue(jsonNode, "destination", null))
                    .numberOfTickets(getIntValue(jsonNode, "numberOfTickets", null))
                    .busType(getStringValue(jsonNode, "busType", null))
                    .priceMin(getDoubleValue(jsonNode, "priceMin", null))
                    .priceMax(getDoubleValue(jsonNode, "priceMax", null))
                    .confidence(getDoubleValue(jsonNode, "confidence", 0.7))
                    .isRoundTrip(getBooleanValue(jsonNode, "isRoundTrip", false))
                    .additionalInfo(originalMessage);
                
                // Parse departureDate
                String dateStr = getStringValue(jsonNode, "departureDate", null);
                if (dateStr != null) {
                    try {
                        builder.departureDate(LocalDate.parse(dateStr));
                    } catch (Exception e) {
                        log.warn("Failed to parse departureDate: {}", dateStr);
                    }
                }
                
                // Parse returnDate (for round-trip)
                String returnDateStr = getStringValue(jsonNode, "returnDate", null);
                if (returnDateStr != null) {
                    try {
                        builder.returnDate(LocalDate.parse(returnDateStr));
                        builder.isRoundTrip(true); // Nếu có returnDate thì chắc chắn là khứ hồi
                    } catch (Exception e) {
                        log.warn("Failed to parse returnDate: {}", returnDateStr);
                    }
                }
                
                return builder.build();
            }
            
        } catch (Exception e) {
            log.error("Error parsing AI response", e);
        }
        
        return extractIntentWithRegex(originalMessage);
    }

    /**
     * Trích xuất JSON từ text response
     */
    private String extractJsonFromText(String text) {
        // Tìm JSON object trong text
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return null;
    }

    /**
     * Fallback: Trích xuất bằng regex
     */
    private SearchIntentDTO extractIntentWithRegex(String message) {
        // ✅ Fix: Check null message
        if (message == null || message.trim().isEmpty()) {
            log.warn("⚠️ Message is null or empty in regex fallback");
            return SearchIntentDTO.builder()
                    .intentType("GENERAL_QUESTION")
                    .confidence(0.0)
                    .build();
        }
        
        SearchIntentDTO.SearchIntentDTOBuilder builder = SearchIntentDTO.builder();
        
        String lowerMessage = message.toLowerCase();
        
        // Xác định intent type
        if (lowerMessage.contains("tìm") || lowerMessage.contains("có") || lowerMessage.contains("chuyến")) {
            builder.intentType("SEARCH_TRIP");
        } else if (lowerMessage.contains("đặt") || lowerMessage.contains("book")) {
            builder.intentType("BOOK_TICKET");
        } else if (lowerMessage.contains("giá") || lowerMessage.contains("bao nhiêu")) {
            builder.intentType("ASK_PRICE");
        } else if (lowerMessage.contains("lịch") || lowerMessage.contains("giờ")) {
            builder.intentType("ASK_SCHEDULE");
        } else {
            builder.intentType("GENERAL_QUESTION");
        }
        
        // Nhận biết khứ hồi
        boolean isRoundTrip = checkIsRoundTrip(lowerMessage);
        builder.isRoundTrip(isRoundTrip);
        
        // Trích xuất locations
        String[] locations = extractLocations(lowerMessage);
        if (locations[0] != null) builder.departure(locations[0]);
        if (locations[1] != null) builder.destination(locations[1]);
        
        // Trích xuất số vé
        Integer tickets = extractNumberOfTickets(lowerMessage);
        if (tickets != null) builder.numberOfTickets(tickets);
        
        // Trích xuất loại xe
        String busType = extractBusType(lowerMessage);
        if (busType != null) builder.busType(busType);
        
        // Trích xuất ngày đi và ngày về
        LocalDate[] dates = extractDatesForRoundTrip(lowerMessage, isRoundTrip);
        if (dates[0] != null) builder.departureDate(dates[0]);
        if (dates[1] != null) {
            builder.returnDate(dates[1]);
            builder.isRoundTrip(true); // Chắc chắn là khứ hồi nếu có ngày về
        }
        
        builder.confidence(0.6);
        builder.additionalInfo(message);
        
        return builder.build();
    }
    
    /**
     * Kiểm tra xem có phải vé khứ hồi không
     */
    private boolean checkIsRoundTrip(String message) {
        // Các từ khóa khứ hồi
        String[] roundTripKeywords = {
            "khứ hồi", "khu hoi", "2 chiều", "hai chiều", "2 chieu", "hai chieu",
            "đi về", "di ve", "cả đi lẫn về", "ca di lan ve",
            "về ngày", "ve ngay", "ngày về", "ngay ve",
            "round trip", "roundtrip", "round-trip"
        };
        
        for (String keyword : roundTripKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Trích xuất ngày đi và ngày về cho khứ hồi
     */
    private LocalDate[] extractDatesForRoundTrip(String message, boolean isRoundTrip) {
        LocalDate[] result = new LocalDate[2]; // [departureDate, returnDate]
        
        // Pattern cho ngày về: "về ngày X" hoặc "ngày về X"
        Pattern returnDatePattern = Pattern.compile("(?:về\\s+ngày|ngày\\s+về)\\s*(\\d{1,2})[-/](\\d{1,2})(?:[-/](\\d{4}))?");
        Matcher returnMatcher = returnDatePattern.matcher(message);
        
        if (returnMatcher.find()) {
            try {
                int day = Integer.parseInt(returnMatcher.group(1));
                int month = Integer.parseInt(returnMatcher.group(2));
                int year = returnMatcher.group(3) != null ? 
                    Integer.parseInt(returnMatcher.group(3)) : LocalDate.now().getYear();
                result[1] = LocalDate.of(year, month, day);
                
                // Nếu ngày về đã qua, lấy năm sau
                if (result[1].isBefore(LocalDate.now())) {
                    result[1] = result[1].plusYears(1);
                }
            } catch (Exception e) {
                log.warn("Failed to parse return date");
            }
        }
        
        // Trích xuất ngày đi bình thường
        result[0] = extractDate(message);
        
        return result;
    }

    /**
     * Trích xuất địa điểm từ tin nhắn
     */
    private String[] extractLocations(String message) {
        String[] result = new String[2]; // [departure, destination]
        
        String[] cities = {
            // Thành phố lớn
            "hà nội", "hải phòng", "sài gòn", "tp.hcm", "hồ chí minh",
            "đà nẵng", "huế", "nha trang", "đà lạt", "vũng tàu",
            "cần thơ", "quy nhơn", "phú quốc", "hạ long", "ninh bình",
            "sapa", "hội an", "phan thiết", "buôn ma thuột",
            
            // Bến xe và địa danh quan trọng
            "giáp bát", "mỹ đình", "yên nghĩa", "nước ngầm", "gia lâm",
            "miền đông", "miền tây", "bến xe miền đông", "bến xe miền tây",
            "bến xe an sương", "an sương", "chợ lớn",
            
            // Tỉnh thành miền Bắc
            "hà giang", "cao bằng", "lào cai", "lai châu", "điện biên",
            "sơn la", "yên bái", "tuyên quang", "phú thọ", "bắc kạn",
            "thái nguyên", "lạng sơn", "bắc giang", "bắc ninh", "hưng yên",
            "hải dương", "nam định", "thái bình", "thanh hóa", "nghệ an",
            
            // Tỉnh thành miền Trung
            "vinh", "quảng bình", "quảng trị", "quảng nam", "quảng ngãi",
            "bình định", "phú yên", "khánh hòa", "lâm đồng", "bình thuận",
            
            // Tỉnh thành miền Nam
            "đồng nai", "bình dương", "tây ninh", "long an", "tiền giang",
            "bến tre", "trà vinh", "vĩnh long", "an giang", "kiên giang",
            "cà mau", "bạc liêu", "sóc trăng", "hậu giang", "đồng tháp"
        };
        
        // Thử tìm pattern "từ X đến/đi Y" hoặc "X đến/đi Y"
        Pattern fromToPattern = Pattern.compile("(từ\\s+)?([a-zà-ỹ\\s]+?)\\s+(đến|đi|->)\\s+([a-zà-ỹ\\s]+)");
        Matcher fromToMatcher = fromToPattern.matcher(message.toLowerCase());
        
        if (fromToMatcher.find()) {
            String from = fromToMatcher.group(2).trim();
            String to = fromToMatcher.group(4).trim();
            
            // Tìm thành phố trong chuỗi from và to
            for (String city : cities) {
                if (from.contains(city)) {
                    result[0] = capitalizeCity(city);
                }
                if (to.contains(city)) {
                    result[1] = capitalizeCity(city);
                }
            }
        }
        
        // Nếu không tìm được bằng pattern, dùng cách cũ
        if (result[0] == null || result[1] == null) {
            for (String city : cities) {
                if (message.contains(city)) {
                    if (result[0] == null) {
                        result[0] = capitalizeCity(city);
                    } else if (result[1] == null && !city.equals(result[0].toLowerCase())) {
                        result[1] = capitalizeCity(city);
                    }
                }
            }
        }
        
        return result;
    }

    private String capitalizeCity(String city) {
        // Capitalize first letter of each word
        String[] words = city.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    /**
     * Trích xuất số vé
     */
    private Integer extractNumberOfTickets(String message) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*(vé|ghế|chỗ|người)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * Trích xuất loại xe
     */
    private String extractBusType(String message) {
        if (message.contains("vip")) return "VIP";
        if (message.contains("giường nằm")) return "Giường nằm";
        if (message.contains("limousine")) return "Limousine";
        if (message.contains("thường")) return "Thường";
        return null;
    }

    /**
     * Trích xuất ngày từ tin nhắn
     */
    private LocalDate extractDate(String message) {
        // Ngày hôm nay
        if (message.contains("hôm nay") || message.contains("bây giờ")) {
            return LocalDate.now();
        }
        
        // Ngày mai
        if (message.contains("ngày mai") || message.contains("mai")) {
            return LocalDate.now().plusDays(1);
        }
        
        // Ngày kia
        if (message.contains("ngày kia")) {
            return LocalDate.now().plusDays(2);
        }
        
        // Cuối tuần
        if (message.contains("cuối tuần")) {
            LocalDate now = LocalDate.now();
            // Tìm thứ 7 tiếp theo
            int daysUntilSaturday = (6 - now.getDayOfWeek().getValue()) % 7;
            if (daysUntilSaturday == 0) daysUntilSaturday = 7; // Nếu hôm nay là thứ 7, lấy thứ 7 tuần sau
            return now.plusDays(daysUntilSaturday);
        }
        
        // Pattern: dd/MM/yyyy hoặc dd-MM-yyyy
        Pattern fullDatePattern = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})");
        Matcher fullDateMatcher = fullDatePattern.matcher(message);
        if (fullDateMatcher.find()) {
            try {
                String dateStr = fullDateMatcher.group(1) + "/" + fullDateMatcher.group(2) + "/" + fullDateMatcher.group(3);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e) {
                log.warn("Failed to parse full date from message");
            }
        }
        
        // Pattern: "9 tháng 11" hoặc "9 tháng 11 năm 2025"
        Pattern monthYearPattern = Pattern.compile("(\\d{1,2})\\s+tháng\\s+(\\d{1,2})(?:\\s+năm\\s+(\\d{4}))?");
        Matcher monthYearMatcher = monthYearPattern.matcher(message);
        if (monthYearMatcher.find()) {
            try {
                int day = Integer.parseInt(monthYearMatcher.group(1));
                int month = Integer.parseInt(monthYearMatcher.group(2));
                int year = monthYearMatcher.group(3) != null ? 
                    Integer.parseInt(monthYearMatcher.group(3)) : 
                    LocalDate.now().getYear();
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                log.warn("Failed to parse month/year date from message");
            }
        }
        
        // Pattern: "9-11" hoặc "9/11" (ngày-tháng, năm hiện tại)
        Pattern shortDatePattern = Pattern.compile("(?:ngày\\s+)?(\\d{1,2})[-/](\\d{1,2})(?![-/]\\d{4})");
        Matcher shortDateMatcher = shortDatePattern.matcher(message);
        if (shortDateMatcher.find()) {
            try {
                int day = Integer.parseInt(shortDateMatcher.group(1));
                int month = Integer.parseInt(shortDateMatcher.group(2));
                int year = LocalDate.now().getYear();
                
                // Nếu ngày đã qua trong năm nay, lấy năm sau
                LocalDate date = LocalDate.of(year, month, day);
                if (date.isBefore(LocalDate.now())) {
                    date = date.plusYears(1);
                }
                
                return date;
            } catch (Exception e) {
                log.warn("Failed to parse short date from message");
            }
        }
        
        return null;
    }

    // Helper methods
    private String getStringValue(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultValue;
    }

    private Integer getIntValue(JsonNode node, String field, Integer defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return Integer.valueOf(node.get(field).asInt());
            } catch (Exception e) {
                log.warn("Failed to parse int value for field '{}': {}", field, e.getMessage());
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Double getDoubleValue(JsonNode node, String field, Double defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return Double.valueOf(node.get(field).asDouble());
            } catch (Exception e) {
                log.warn("Failed to parse double value for field '{}': {}", field, e.getMessage());
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private Boolean getBooleanValue(JsonNode node, String field, Boolean defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return Boolean.valueOf(node.get(field).asBoolean());
            } catch (Exception e) {
                log.warn("Failed to parse boolean value for field '{}': {}", field, e.getMessage());
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

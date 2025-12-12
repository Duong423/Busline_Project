package com.busify.project.chat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.busify.project.chat.dto.AIResponseDTO;
import com.busify.project.chat.dto.SearchIntentDTO;
import com.busify.project.chat.dto.TripSearchResultDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service xử lý chat AI thông minh với khả năng tìm kiếm sản phẩm
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartChatBotService {

    private final IntentExtractionService intentExtractionService;
    private final TripSearchService tripSearchService;
    private final OpenAIService openAIService;
    private final ConversationContextService conversationContextService;
    private final AIChatHistoryService chatHistoryService;

    /**
     * Xử lý tin nhắn thông minh - trích xuất ý định và tìm kiếm sản phẩm
     * Sử dụng lịch sử chat để AI hiểu ngữ cảnh hội thoại tốt hơn
     */
    public AIResponseDTO processSmartMessage(String userMessage, String userEmail) {
        try {
            log.info("Processing smart message from user: {}", userEmail);

            // 0. Lưu tin nhắn của user vào lịch sử
            chatHistoryService.saveUserMessage(userEmail, userMessage);

            // 1. Trích xuất ý định từ tin nhắn (SỬ DỤNG LỊCH SỬ CHAT)
            SearchIntentDTO rawIntent = intentExtractionService.extractSearchIntentWithHistory(userEmail, userMessage);
            log.info("Extracted raw intent with history: {}", rawIntent.getIntentType());

            // 2. Merge với context từ các tin nhắn trước
            SearchIntentDTO intent = conversationContextService.mergeContextWithIntent(userEmail, rawIntent);
            log.info("Merged intent with context - Departure: {}, Destination: {}", 
                intent.getDeparture(), intent.getDestination());

            // 3. Cập nhật context với thông tin mới
            conversationContextService.updateContext(userEmail, intent, userMessage);

            // 4. Xử lý theo loại ý định
            AIResponseDTO response = switch (intent.getIntentType()) {
                case "SEARCH_TRIP" -> handleSearchTrip(intent, userMessage, userEmail);
                case "ASK_PRICE" -> handleAskPrice(intent, userMessage, userEmail);
                case "ASK_SCHEDULE" -> handleAskSchedule(intent, userMessage, userEmail);
                case "BOOK_TICKET" -> handleBookTicket(intent, userMessage, userEmail);
                default -> handleGeneralQuestion(userMessage, userEmail);
            };

            // 5. Lưu response của AI vào lịch sử
            chatHistoryService.saveAssistantMessage(userEmail, response.getContent());

            response.setTimestamp(System.currentTimeMillis());
            return response;

        } catch (Exception e) {
            log.error("Error processing smart message", e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Xử lý tìm kiếm chuyến đi
     */
    private AIResponseDTO handleSearchTrip(SearchIntentDTO intent, String userMessage, String userEmail) {
        log.info("Handling trip search for user: {}", userEmail);

        // Kiểm tra thông tin đủ để tìm kiếm không
        if (intent.getDeparture() == null || intent.getDestination() == null) {
            return createNeedMoreInfoResponse(intent, 
                "Để tìm kiếm chuyến đi phù hợp, bạn vui lòng cho tôi biết:\n\n" +
                (intent.getDeparture() == null ? "📍 Điểm đi của bạn là đâu?\n" : "") +
                (intent.getDestination() == null ? "📍 Bạn muốn đến đâu?\n" : "") +
                (intent.getDepartureDate() == null ? "📅 Bạn muốn đi vào ngày nào?\n" : "") +
                "\nVí dụ: Tôi muốn đi từ Hà Nội đến Đà Nẵng vào ngày mai"
            );
        }
        
        // Kiểm tra nếu là khứ hồi nhưng thiếu ngày về
        if (Boolean.TRUE.equals(intent.getIsRoundTrip()) && intent.getReturnDate() == null) {
            return createNeedMoreInfoResponse(intent,
                "🔄 Bạn muốn đặt vé **khứ hồi** từ **" + intent.getDeparture() + "** đến **" + intent.getDestination() + "**.\n\n" +
                "📅 Bạn đã chọn ngày đi: " + (intent.getDepartureDate() != null ? intent.getDepartureDate() : "chưa chọn") + "\n" +
                "📅 Vui lòng cho tôi biết **ngày về** của bạn?\n\n" +
                "Ví dụ: Về ngày 15/12 hoặc Ngày về là 15/12"
            );
        }

        // Tìm kiếm chuyến đi (chiều đi)
        List<TripSearchResultDTO> trips = tripSearchService.searchTrips(intent);
        
        // Nếu là khứ hồi, tìm thêm chuyến về
        List<TripSearchResultDTO> returnTrips = new ArrayList<>();
        if (Boolean.TRUE.equals(intent.getIsRoundTrip()) && intent.getReturnDate() != null) {
            // Tạo intent cho chiều về (đảo ngược departure và destination)
            SearchIntentDTO returnIntent = SearchIntentDTO.builder()
                .intentType(intent.getIntentType())
                .departure(intent.getDestination()) // Đảo ngược
                .destination(intent.getDeparture()) // Đảo ngược
                .departureDate(intent.getReturnDate()) // Ngày về
                .numberOfTickets(intent.getNumberOfTickets())
                .busType(intent.getBusType())
                .build();
            
            returnTrips = tripSearchService.searchTrips(returnIntent);
            log.info("Found {} return trips for round-trip search", returnTrips.size());
        }

        if (trips.isEmpty()) {
            return createNoResultResponse(intent, 
                "Rất tiếc, tôi không tìm thấy chuyến đi phù hợp từ " + 
                intent.getDeparture() + " đến " + intent.getDestination() +
                (intent.getDepartureDate() != null ? " vào " + intent.getDepartureDate() : "") + ".\n\n" +
                "Bạn có thể:\n" +
                "• Thử tìm kiếm ngày khác\n" +
                "• Linh hoạt về điểm đi/điểm đến\n" +
                "• Liên hệ nhân viên hỗ trợ để được tư vấn thêm"
            );
        }

        // Tạo response text từ AI
        String aiText = generateSearchResultText(intent, trips, returnTrips, userEmail);

        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.PRODUCT_SEARCH)
            .content(aiText)
            .searchIntent(intent)
            .trips(trips)
            .totalResults(trips.size())
            .needMoreInfo(false)
            .suggestedQuestions(generateSuggestedQuestions(intent))
            .build();
    }

    /**
     * Xử lý hỏi giá
     */
    private AIResponseDTO handleAskPrice(SearchIntentDTO intent, String userMessage, String userEmail) {
        // Tìm kiếm để lấy thông tin giá
        if (intent.getDeparture() != null && intent.getDestination() != null) {
            List<TripSearchResultDTO> trips = tripSearchService.searchTrips(intent);
            
            if (!trips.isEmpty()) {
                String priceInfo = generatePriceInfo(trips, intent);
                
                return AIResponseDTO.builder()
                    .type(AIResponseDTO.ResponseType.PRODUCT_SEARCH)
                    .content(priceInfo)
                    .searchIntent(intent)
                    .trips(trips)
                    .totalResults(trips.size())
                    .needMoreInfo(false)
                    .suggestedQuestions(List.of(
                        "Cho tôi xem chuyến rẻ nhất",
                        "Xe VIP giá bao nhiêu?",
                        "Có khuyến mãi gì không?"
                    ))
                    .build();
            }
        }

        return handleGeneralQuestion(userMessage, userEmail);
    }

    /**
     * Xử lý hỏi lịch trình
     */
    private AIResponseDTO handleAskSchedule(SearchIntentDTO intent, String userMessage, String userEmail) {
        if (intent.getDeparture() != null && intent.getDestination() != null) {
            List<TripSearchResultDTO> trips = tripSearchService.searchTrips(intent);
            
            if (!trips.isEmpty()) {
                String scheduleInfo = generateScheduleInfo(trips, intent);
                
                return AIResponseDTO.builder()
                    .type(AIResponseDTO.ResponseType.PRODUCT_SEARCH)
                    .content(scheduleInfo)
                    .searchIntent(intent)
                    .trips(trips)
                    .totalResults(trips.size())
                    .needMoreInfo(false)
                    .suggestedQuestions(List.of(
                        "Chuyến sớm nhất mấy giờ?",
                        "Có chuyến tối không?",
                        "Thời gian di chuyển bao lâu?"
                    ))
                    .build();
            }
        }

        return handleGeneralQuestion(userMessage, userEmail);
    }

    /**
     * Xử lý yêu cầu đặt vé
     */
    private AIResponseDTO handleBookTicket(SearchIntentDTO intent, String userMessage, String userEmail) {
        String bookingGuide = "Để đặt vé xe trên Busify, bạn làm theo các bước sau:\n\n" +
            "1️⃣ **Chọn chuyến đi**\n" +
            "   Tìm kiếm và chọn chuyến phù hợp với lịch trình của bạn\n\n" +
            "2️⃣ **Chọn ghế ngồi**\n" +
            "   Xem sơ đồ ghế và chọn vị trí mong muốn\n\n" +
            "3️⃣ **Nhập thông tin**\n" +
            "   Điền họ tên, số điện thoại, email\n\n" +
            "4️⃣ **Thanh toán**\n" +
            "   Chọn phương thức: VNPay, PayPal, hoặc chuyển khoản\n\n" +
            "5️⃣ **Nhận vé điện tử**\n" +
            "   Vé sẽ được gửi qua email sau khi thanh toán thành công\n\n";

        // Nếu có thông tin cụ thể, tìm kiếm luôn
        if (intent.getDeparture() != null && intent.getDestination() != null) {
            List<TripSearchResultDTO> trips = tripSearchService.searchTrips(intent);
            
            if (!trips.isEmpty()) {
                bookingGuide = "Tôi tìm thấy " + trips.size() + " chuyến phù hợp cho bạn!\n\n" + bookingGuide;
                
                return AIResponseDTO.builder()
                    .type(AIResponseDTO.ResponseType.PRODUCT_SEARCH)
                    .content(bookingGuide)
                    .searchIntent(intent)
                    .trips(trips)
                    .totalResults(trips.size())
                    .needMoreInfo(false)
                    .suggestedQuestions(List.of(
                        "Làm sao chọn ghế?",
                        "Thanh toán thế nào?",
                        "Có cần tài khoản không?"
                    ))
                    .build();
            }
        }

        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.BOOKING_GUIDE)
            .content(bookingGuide)
            .searchIntent(intent)
            .trips(new ArrayList<>())
            .totalResults(0)
            .needMoreInfo(true)
            .suggestedQuestions(List.of(
                "Tìm chuyến từ Hà Nội đi Đà Nẵng",
                "Các bước đặt vé là gì?",
                "Thanh toán bằng gì?"
            ))
            .build();
    }

    /**
     * Xử lý câu hỏi chung
     */
    private AIResponseDTO handleGeneralQuestion(String userMessage, String userEmail) {
        String aiResponse = openAIService.getChatGPTResponse(userMessage, userEmail);
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            aiResponse = generateFallbackResponse(userMessage);
        }

        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.TEXT)
            .content(aiResponse)
            .searchIntent(null)
            .trips(new ArrayList<>())
            .totalResults(0)
            .needMoreInfo(false)
            .suggestedQuestions(List.of(
                "Tìm vé xe đi Đà Nẵng",
                "Giá vé như thế nào?",
                "Làm sao đặt vé?"
            ))
            .build();
    }

    /**
     * Tạo response cần thêm thông tin
     */
    private AIResponseDTO createNeedMoreInfoResponse(SearchIntentDTO intent, String message) {
        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.NEED_MORE_INFO)
            .content(message)
            .searchIntent(intent)
            .trips(new ArrayList<>())
            .totalResults(0)
            .needMoreInfo(true)
            .suggestedQuestions(List.of(
                "Tìm vé từ Hà Nội đến Đà Nẵng",
                "Xe đi Sài Gòn ngày mai",
                "Vé xe VIP Huế"
            ))
            .build();
    }

    /**
     * Tạo response không có kết quả
     */
    private AIResponseDTO createNoResultResponse(SearchIntentDTO intent, String message) {
        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.TEXT)
            .content(message)
            .searchIntent(intent)
            .trips(new ArrayList<>())
            .totalResults(0)
            .needMoreInfo(false)
            .suggestedQuestions(List.of(
                "Tìm ngày khác",
                "Xem tuyến khác",
                "Liên hệ nhân viên"
            ))
            .build();
    }

    /**
     * Tạo response lỗi
     */
    private AIResponseDTO createErrorResponse(String errorMessage) {
        return AIResponseDTO.builder()
            .type(AIResponseDTO.ResponseType.ERROR)
            .content("Xin lỗi, đã có lỗi xảy ra: " + errorMessage)
            .trips(new ArrayList<>())
            .totalResults(0)
            .needMoreInfo(false)
            .build();
    }

    /**
     * Tạo text mô tả kết quả tìm kiếm
     */
    private String generateSearchResultText(SearchIntentDTO intent, List<TripSearchResultDTO> trips, List<TripSearchResultDTO> returnTrips, String userEmail) {
        StringBuilder text = new StringBuilder();
        
        // Kiểm tra nếu là khứ hồi
        boolean isRoundTrip = Boolean.TRUE.equals(intent.getIsRoundTrip()) && intent.getReturnDate() != null;
        
        if (isRoundTrip) {
            text.append("🔄 **VÉ KHỨ HỒI**\n\n");
        }
        
        text.append("🎉 Tuyệt vời! Tôi tìm thấy **").append(trips.size())
            .append(" chuyến xe** từ **").append(intent.getDeparture())
            .append("** đến **").append(intent.getDestination()).append("**");
        
        if (intent.getDepartureDate() != null) {
            text.append(" vào ngày **").append(intent.getDepartureDate()).append("**");
        }
        
        text.append(".\n\n");
        
        // CHIỀU ĐI
        if (isRoundTrip) {
            text.append("📤 **CHIỀU ĐI** (").append(intent.getDepartureDate()).append("):\n\n");
        }
        
        // Thông tin chi tiết về các chuyến (top 3)
        int displayCount = Math.min(3, trips.size());
        for (int i = 0; i < displayCount; i++) {
            TripSearchResultDTO trip = trips.get(i);
            text.append("🚌 **Chuyến ").append(i + 1).append("**: ")
                .append(trip.getBusType())
                .append(" - Khởi hành lúc ")
                .append(trip.getDepartureTime().toLocalTime())
                .append("\n   💰 Giá: ");
            
            if (trip.getHasPromotion() && trip.getDiscountedPrice() != null) {
                text.append("~~").append(String.format("%,.0f", trip.getPrice())).append("đ~~ → ")
                    .append("**").append(String.format("%,.0f", trip.getDiscountedPrice())).append("đ** 🎁\n");
            } else {
                text.append("**").append(String.format("%,.0f", trip.getPrice())).append("đ**\n");
            }
            
            text.append("   🪑 Còn ").append(trip.getAvailableSeats()).append(" ghế trống\n");
            
            if (trip.getRating() != null) {
                text.append("   ⭐ Đánh giá: ").append(trip.getRating()).append("/5\n");
            }
            
            text.append("\n");
        }
        
        if (trips.size() > 3) {
            text.append("_...và ").append(trips.size() - 3).append(" chuyến khác nữa!_\n\n");
        }
        
        // CHIỀU VỀ (nếu là khứ hồi)
        if (isRoundTrip && returnTrips != null && !returnTrips.isEmpty()) {
            text.append("📥 **CHIỀU VỀ** (").append(intent.getReturnDate()).append("):\n");
            text.append("Từ **").append(intent.getDestination()).append("** về **").append(intent.getDeparture()).append("**\n\n");
            
            int returnDisplayCount = Math.min(3, returnTrips.size());
            for (int i = 0; i < returnDisplayCount; i++) {
                TripSearchResultDTO trip = returnTrips.get(i);
                text.append("🚌 **Chuyến ").append(i + 1).append("**: ")
                    .append(trip.getBusType())
                    .append(" - Khởi hành lúc ")
                    .append(trip.getDepartureTime().toLocalTime())
                    .append("\n   💰 Giá: ");
                
                if (trip.getHasPromotion() && trip.getDiscountedPrice() != null) {
                    text.append("~~").append(String.format("%,.0f", trip.getPrice())).append("đ~~ → ")
                        .append("**").append(String.format("%,.0f", trip.getDiscountedPrice())).append("đ** 🎁\n");
                } else {
                    text.append("**").append(String.format("%,.0f", trip.getPrice())).append("đ**\n");
                }
                
                text.append("   🪑 Còn ").append(trip.getAvailableSeats()).append(" ghế trống\n\n");
            }
            
            if (returnTrips.size() > 3) {
                text.append("_...và ").append(returnTrips.size() - 3).append(" chuyến về khác!_\n\n");
            }
            
            // Tính tổng giá khứ hồi
            double goPrice = trips.get(0).getDiscountedPrice() != null ? 
                trips.get(0).getDiscountedPrice() : trips.get(0).getPrice();
            double returnPrice = returnTrips.get(0).getDiscountedPrice() != null ? 
                returnTrips.get(0).getDiscountedPrice() : returnTrips.get(0).getPrice();
            
            text.append("💵 **Tổng giá khứ hồi (ước tính)**: **")
                .append(String.format("%,.0f", goPrice + returnPrice)).append("đ**\n\n");
        } else if (isRoundTrip) {
            text.append("⚠️ **Chiều về**: Không tìm thấy chuyến phù hợp vào ngày ")
                .append(intent.getReturnDate()).append("\n\n");
        }
        
        text.append("Bạn có thể chọn chuyến phù hợp và đặt vé ngay nhé! 🎫");
        
        return text.toString();
    }

    /**
     * Tạo thông tin giá
     */
    private String generatePriceInfo(List<TripSearchResultDTO> trips, SearchIntentDTO intent) {
        double minPrice = trips.stream()
            .mapToDouble(t -> t.getDiscountedPrice() != null ? t.getDiscountedPrice() : t.getPrice())
            .min().orElse(0);
        
        double maxPrice = trips.stream()
            .mapToDouble(TripSearchResultDTO::getPrice)
            .max().orElse(0);
        
        return "💰 **Thông tin giá vé** từ " + intent.getDeparture() + " đến " + intent.getDestination() + ":\n\n" +
            "• Giá thấp nhất: **" + String.format("%,.0f", minPrice) + "đ**\n" +
            "• Giá cao nhất: **" + String.format("%,.0f", maxPrice) + "đ**\n" +
            "• Có " + trips.stream().filter(TripSearchResultDTO::getHasPromotion).count() + 
            " chuyến đang có khuyến mãi 🎁\n\n" +
            "Tôi đã tìm thấy " + trips.size() + " chuyến phù hợp. Bạn muốn xem chi tiết không?";
    }

    /**
     * Tạo thông tin lịch trình
     */
    private String generateScheduleInfo(List<TripSearchResultDTO> trips, SearchIntentDTO intent) {
        TripSearchResultDTO earliest = trips.stream()
            .min((t1, t2) -> t1.getDepartureTime().compareTo(t2.getDepartureTime()))
            .orElse(null);
        
        TripSearchResultDTO latest = trips.stream()
            .max((t1, t2) -> t1.getDepartureTime().compareTo(t2.getDepartureTime()))
            .orElse(null);
        
        return "🕐 **Lịch trình** từ " + intent.getDeparture() + " đến " + intent.getDestination() + ":\n\n" +
            "• Chuyến sớm nhất: " + (earliest != null ? earliest.getDepartureTime().toLocalTime() : "N/A") + "\n" +
            "• Chuyến muộn nhất: " + (latest != null ? latest.getDepartureTime().toLocalTime() : "N/A") + "\n" +
            "• Tổng số chuyến: " + trips.size() + " chuyến\n\n" +
            "Bạn muốn đi chuyến nào?";
    }

    /**
     * Tạo câu hỏi gợi ý
     */
    private List<String> generateSuggestedQuestions(SearchIntentDTO intent) {
        List<String> questions = new ArrayList<>();
        
        if (intent.getDeparture() != null && intent.getDestination() != null) {
            questions.add("Chuyến nào rẻ nhất?");
            questions.add("Xe VIP có không?");
            questions.add("Còn ghế trống không?");
        } else {
            questions.add("Tìm vé đi Đà Nẵng");
            questions.add("Giá vé như thế nào?");
            questions.add("Làm sao đặt vé?");
        }
        
        return questions;
    }

    /**
     * Phản hồi fallback
     */
    private String generateFallbackResponse(String userMessage) {
        return "Tôi là trợ lý ảo của Busify. Tôi có thể giúp bạn:\n\n" +
            "🔍 Tìm kiếm chuyến xe\n" +
            "💰 Tra cứu giá vé\n" +
            "📅 Xem lịch trình\n" +
            "🎫 Hướng dẫn đặt vé\n" +
            "❓ Giải đáp thắc mắc\n\n" +
            "Ví dụ: \"Tìm vé từ Hà Nội đi Đà Nẵng ngày mai\"\n\n" +
            "Bạn cần hỗ trợ gì?";
    }
}

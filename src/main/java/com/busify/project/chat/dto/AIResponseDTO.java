package com.busify.project.chat.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho phản hồi của AI chatbot kèm kết quả tìm kiếm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponseDTO {
    
    /**
     * ID tin nhắn
     */
    private Long messageId;
    
    /**
     * Nội dung phản hồi từ AI
     */
    private String content;
    
    /**
     * Loại phản hồi: TEXT, PRODUCT_SEARCH, BOOKING_GUIDE, ERROR
     */
    private ResponseType type;
    
    /**
     * Ý định được trích xuất từ câu hỏi
     */
    private SearchIntentDTO searchIntent;
    
    /**
     * Danh sách sản phẩm/chuyến đi tìm được (chiều đi)
     */
    private List<TripSearchResultDTO> trips;
    
    /**
     * Danh sách chuyến về (cho vé khứ hồi)
     */
    private List<TripSearchResultDTO> returnTrips;
    
    /**
     * Tổng số kết quả chiều đi
     */
    private Integer totalResults;
    
    /**
     * Tổng số kết quả chiều về
     */
    private Integer returnTotalResults;
    
    /**
     * Có cần thêm thông tin không
     */
    private Boolean needMoreInfo;
    
    /**
     * Câu hỏi gợi ý
     */
    private List<String> suggestedQuestions;
    
    /**
     * Timestamp
     */
    private Long timestamp;
    
    public enum ResponseType {
        TEXT,              // Chỉ có text
        PRODUCT_SEARCH,    // Có kết quả tìm kiếm
        BOOKING_GUIDE,     // Hướng dẫn đặt vé
        ERROR,             // Lỗi
        NEED_MORE_INFO     // Cần thêm thông tin
    }
}

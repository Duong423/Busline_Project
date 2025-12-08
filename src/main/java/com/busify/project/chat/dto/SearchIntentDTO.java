package com.busify.project.chat.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin ý định tìm kiếm được trích xuất từ câu chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchIntentDTO {
    
    /**
     * Loại ý định: SEARCH_TRIP, BOOK_TICKET, ASK_PRICE, ASK_SCHEDULE, GENERAL_QUESTION
     */
    private String intentType;
    
    /**
     * Điểm đi
     */
    private String departure;
    
    /**
     * Điểm đến
     */
    private String destination;
    
    /**
     * Ngày đi (format: yyyy-MM-dd)
     */
    private LocalDate departureDate;
    
    /**
     * Ngày về (format: yyyy-MM-dd) - dùng cho vé khứ hồi
     */
    private LocalDate returnDate;
    
    /**
     * Có phải vé khứ hồi không
     */
    private Boolean isRoundTrip;
    
    /**
     * Số lượng vé
     */
    private Integer numberOfTickets;
    
    /**
     * Loại xe (VIP, thường, giường nằm)
     */
    private String busType;
    
    /**
     * Khoảng giá (min)
     */
    private Double priceMin;
    
    /**
     * Khoảng giá (max)
     */
    private Double priceMax;
    
    /**
     * Độ tin cậy của việc trích xuất (0-1)
     */
    private Double confidence;
    
    /**
     * Thông tin bổ sung
     */
    private String additionalInfo;
}

package com.busify.project.chat.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO để trả về kết quả tìm kiếm vé xe trong chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearchResultDTO {
    
    /**
     * ID của chuyến đi
     */
    private Long tripId;
    
    /**
     * Tên tuyến đường
     */
    private String routeName;
    
    /**
     * Điểm đi
     */
    private String departureLocation;
    
    /**
     * Điểm đến
     */
    private String arrivalLocation;
    
    /**
     * Thời gian khởi hành
     */
    private LocalDateTime departureTime;
    
    /**
     * Thời gian đến
     */
    private LocalDateTime arrivalTime;
    
    /**
     * Giá vé
     */
    private Double price;
    
    /**
     * Số ghế còn trống
     */
    private Integer availableSeats;
    
    /**
     * Loại xe
     */
    private String busType;
    
    /**
     * Biển số xe
     */
    private String busPlate;
    
    /**
     * Tiện ích (WiFi, điều hòa, etc.)
     */
    private List<String> amenities;
    
    /**
     * Rating trung bình
     */
    private Double rating;
    
    /**
     * Có khuyến mãi không
     */
    private Boolean hasPromotion;
    
    /**
     * Giá sau khuyến mãi
     */
    private Double discountedPrice;
    
    /**
     * Link ảnh xe
     */
    private String imageUrl;
}

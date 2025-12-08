package com.busify.project.chat.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity lưu trạng thái hội thoại của người dùng
 * Giúp AI hiểu được context của các tin nhắn liên tiếp
 */
@Entity
@Table(name = "conversation_contexts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email người dùng
     */
    @Column(nullable = false)
    private String userEmail;

    /**
     * Session ID (dùng để phân biệt các phiên chat khác nhau)
     */
    private String sessionId;

    /**
     * Loại ý định hiện tại (SEARCH_TRIP, BOOK_TICKET, ASK_PRICE, ASK_SCHEDULE)
     */
    private String currentIntent;

    /**
     * Điểm đi đã được xác định
     */
    private String departure;

    /**
     * Điểm đến đã được xác định
     */
    private String destination;

    /**
     * Ngày đi đã được xác định
     */
    private LocalDate departureDate;
    
    /**
     * Ngày về (cho vé khứ hồi)
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
     * Loại xe
     */
    private String busType;

    /**
     * Giá tối thiểu
     */
    private Double priceMin;

    /**
     * Giá tối đa
     */
    private Double priceMax;

    /**
     * Tin nhắn cuối cùng của người dùng
     */
    @Column(columnDefinition = "TEXT")
    private String lastUserMessage;

    /**
     * Thời gian tạo context
     */
    private LocalDateTime createdAt;

    /**
     * Thời gian cập nhật cuối cùng
     */
    private LocalDateTime updatedAt;

    /**
     * Thời gian hết hạn của context (sau X phút không hoạt động thì clear)
     */
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Context hết hạn sau 30 phút không hoạt động
        expiresAt = LocalDateTime.now().plusMinutes(30);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Gia hạn thêm 30 phút khi có hoạt động mới
        expiresAt = LocalDateTime.now().plusMinutes(30);
    }
}

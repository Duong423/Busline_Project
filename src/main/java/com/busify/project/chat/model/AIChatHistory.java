package com.busify.project.chat.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity lưu lịch sử tin nhắn với AI chatbot
 * Giúp AI hiểu được ngữ cảnh từ các tin nhắn trước đó
 */
@Entity
@Table(name = "ai_chat_history", indexes = {
    @Index(name = "idx_ai_chat_history_user_email", columnList = "userEmail"),
    @Index(name = "idx_ai_chat_history_session_id", columnList = "sessionId"),
    @Index(name = "idx_ai_chat_history_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email người dùng
     */
    @Column(nullable = false)
    private String userEmail;

    /**
     * Session ID để nhóm các tin nhắn trong cùng phiên chat
     */
    @Column(nullable = false)
    private String sessionId;

    /**
     * Vai trò: "user" hoặc "assistant"
     */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * Nội dung tin nhắn
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Thời gian tạo tin nhắn
     */
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

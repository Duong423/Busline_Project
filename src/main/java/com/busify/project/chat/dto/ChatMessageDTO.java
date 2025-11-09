package com.busify.project.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    @JsonAlias("message") // ✅ Accept both "content" and "message" field
    private String content;
    private String sender;
    private String recipient; // Dùng cho chat 1-1
    private MessageType type;
    private String roomId;
    private Long timestamp; // Sử dụng timestamp dạng Long (milliseconds) để tương thích với WebSocket

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM_ASSIGN // Thêm loại tin nhắn mới
    }

}
package com.busify.project.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.busify.project.chat.model.AIChatHistory;
import com.busify.project.chat.model.ConversationContext;
import com.busify.project.chat.repository.AIChatHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý lịch sử chat với AI
 * Lưu và lấy lịch sử tin nhắn để AI hiểu ngữ cảnh
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatHistoryService {

    private final AIChatHistoryRepository chatHistoryRepository;
    private final ConversationContextService conversationContextService;
    
    // Số tin nhắn tối đa gửi cho AI để làm context
    private static final int MAX_HISTORY_MESSAGES = 10;
    
    /**
     * Lưu tin nhắn của user vào lịch sử
     */
    @Transactional
    public void saveUserMessage(String userEmail, String content) {
        ConversationContext context = conversationContextService.getOrCreateContext(userEmail);
        
        AIChatHistory message = AIChatHistory.builder()
            .userEmail(userEmail)
            .sessionId(context.getSessionId())
            .role("user")
            .content(content)
            .build();
        
        chatHistoryRepository.save(message);
        log.debug("Saved user message for {}: {}", userEmail, content.substring(0, Math.min(50, content.length())));
    }
    
    /**
     * Lưu tin nhắn phản hồi của AI vào lịch sử
     */
    @Transactional
    public void saveAssistantMessage(String userEmail, String content) {
        ConversationContext context = conversationContextService.getOrCreateContext(userEmail);
        
        AIChatHistory message = AIChatHistory.builder()
            .userEmail(userEmail)
            .sessionId(context.getSessionId())
            .role("assistant")
            .content(content)
            .build();
        
        chatHistoryRepository.save(message);
        log.debug("Saved assistant message for {}", userEmail);
    }
    
    /**
     * Lấy lịch sử chat gần đây để gửi cho AI
     * Trả về theo thứ tự từ cũ đến mới
     */
    public List<AIChatHistory> getRecentHistory(String userEmail) {
        ConversationContext context = conversationContextService.getOrCreateContext(userEmail);
        
        List<AIChatHistory> history = chatHistoryRepository.findRecentMessages(
            userEmail, 
            context.getSessionId(), 
            MAX_HISTORY_MESSAGES
        );
        
        // Đảo ngược để có thứ tự từ cũ đến mới
        Collections.reverse(history);
        
        log.debug("Retrieved {} history messages for user {}", history.size(), userEmail);
        return history;
    }
    
    /**
     * Tạo danh sách messages để gửi cho AI (bao gồm lịch sử)
     */
    public List<OpenRouterService.Message> buildMessagesWithHistory(
            String userEmail, 
            String systemPrompt, 
            String currentUserMessage) {
        
        List<OpenRouterService.Message> messages = new ArrayList<>();
        
        // 1. System prompt
        messages.add(new OpenRouterService.Message("system", systemPrompt));
        
        // 2. Lịch sử chat trước đó
        List<AIChatHistory> history = getRecentHistory(userEmail);
        for (AIChatHistory h : history) {
            messages.add(new OpenRouterService.Message(h.getRole(), h.getContent()));
        }
        
        // 3. Tin nhắn hiện tại của user
        messages.add(new OpenRouterService.Message("user", currentUserMessage));
        
        log.info("Built {} messages for AI (including {} history messages)", 
            messages.size(), history.size());
        
        return messages;
    }
    
    /**
     * Xóa lịch sử chat của user (khi bắt đầu phiên mới)
     */
    @Transactional
    public void clearHistory(String userEmail) {
        ConversationContext context = conversationContextService.getOrCreateContext(userEmail);
        chatHistoryRepository.deleteByUserEmailAndSessionId(userEmail, context.getSessionId());
        log.info("Cleared chat history for user {}", userEmail);
    }
    
    /**
     * Dọn dẹp lịch sử chat cũ hơn 24 giờ
     * Chạy mỗi ngày lúc 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        chatHistoryRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up AI chat history older than {}", cutoff);
    }
}

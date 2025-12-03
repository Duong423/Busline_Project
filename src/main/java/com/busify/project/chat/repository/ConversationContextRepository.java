package com.busify.project.chat.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.busify.project.chat.model.ConversationContext;

@Repository
public interface ConversationContextRepository extends JpaRepository<ConversationContext, Long> {

    /**
     * Tìm context của user (lấy context mới nhất)
     */
    Optional<ConversationContext> findFirstByUserEmailOrderByUpdatedAtDesc(String userEmail);

    /**
     * Tìm context theo session ID
     */
    Optional<ConversationContext> findBySessionId(String sessionId);

    /**
     * Tìm context của user theo session ID
     */
    Optional<ConversationContext> findByUserEmailAndSessionId(String userEmail, String sessionId);

    /**
     * Xóa các context đã hết hạn
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}

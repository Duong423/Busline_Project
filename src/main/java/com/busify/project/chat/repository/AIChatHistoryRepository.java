package com.busify.project.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.busify.project.chat.model.AIChatHistory;

@Repository
public interface AIChatHistoryRepository extends JpaRepository<AIChatHistory, Long> {

    /**
     * Lấy lịch sử chat gần đây của user theo sessionId
     * Giới hạn số lượng tin nhắn để không quá tải context
     */
    @Query("SELECT h FROM AIChatHistory h WHERE h.userEmail = :userEmail AND h.sessionId = :sessionId ORDER BY h.createdAt DESC")
    List<AIChatHistory> findRecentByUserEmailAndSessionId(
        @Param("userEmail") String userEmail, 
        @Param("sessionId") String sessionId
    );
    
    /**
     * Lấy N tin nhắn gần nhất của user
     */
    @Query("SELECT h FROM AIChatHistory h WHERE h.userEmail = :userEmail AND h.sessionId = :sessionId ORDER BY h.createdAt DESC LIMIT :limit")
    List<AIChatHistory> findRecentMessages(
        @Param("userEmail") String userEmail,
        @Param("sessionId") String sessionId,
        @Param("limit") int limit
    );
    
    /**
     * Xóa tin nhắn cũ hơn một thời điểm
     */
    @Modifying
    @Query("DELETE FROM AIChatHistory h WHERE h.createdAt < :cutoffTime")
    void deleteOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Xóa tất cả tin nhắn của một session
     */
    @Modifying
    @Query("DELETE FROM AIChatHistory h WHERE h.userEmail = :userEmail AND h.sessionId = :sessionId")
    void deleteByUserEmailAndSessionId(@Param("userEmail") String userEmail, @Param("sessionId") String sessionId);
    
    /**
     * Đếm số tin nhắn trong session
     */
    @Query("SELECT COUNT(h) FROM AIChatHistory h WHERE h.userEmail = :userEmail AND h.sessionId = :sessionId")
    long countByUserEmailAndSessionId(@Param("userEmail") String userEmail, @Param("sessionId") String sessionId);
}

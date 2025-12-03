package com.busify.project.chat.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.busify.project.chat.dto.SearchIntentDTO;
import com.busify.project.chat.model.ConversationContext;
import com.busify.project.chat.repository.ConversationContextRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý trạng thái hội thoại của người dùng
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationContextService {

    private final ConversationContextRepository contextRepository;

    /**
     * Lấy hoặc tạo context mới cho user
     */
    @Transactional
    public ConversationContext getOrCreateContext(String userEmail) {
        Optional<ConversationContext> existingContext = 
            contextRepository.findFirstByUserEmailOrderByUpdatedAtDesc(userEmail);

        if (existingContext.isPresent()) {
            ConversationContext context = existingContext.get();
            // Kiểm tra xem context có hết hạn không
            if (context.getExpiresAt().isAfter(LocalDateTime.now())) {
                log.info("Found existing context for user: {}", userEmail);
                return context;
            } else {
                log.info("Context expired, creating new one for user: {}", userEmail);
            }
        }

        // Tạo context mới
        ConversationContext newContext = ConversationContext.builder()
            .userEmail(userEmail)
            .sessionId(UUID.randomUUID().toString())
            .build();

        return contextRepository.save(newContext);
    }

    /**
     * Cập nhật context với thông tin mới từ SearchIntentDTO
     */
    @Transactional
    public ConversationContext updateContext(String userEmail, SearchIntentDTO intent, String userMessage) {
        ConversationContext context = getOrCreateContext(userEmail);

        log.info("Updating context for user: {} with intent: {}", userEmail, intent.getIntentType());

        // Cập nhật intent type nếu có
        if (intent.getIntentType() != null && !intent.getIntentType().equals("GENERAL_QUESTION")) {
            context.setCurrentIntent(intent.getIntentType());
        }

        // Cập nhật departure - ưu tiên giữ giá trị cũ nếu giá trị mới null
        if (intent.getDeparture() != null) {
            context.setDeparture(intent.getDeparture());
        }

        // Cập nhật destination
        if (intent.getDestination() != null) {
            context.setDestination(intent.getDestination());
        }

        // Cập nhật departureDate
        if (intent.getDepartureDate() != null) {
            context.setDepartureDate(intent.getDepartureDate());
        }

        // Cập nhật numberOfTickets
        if (intent.getNumberOfTickets() != null) {
            context.setNumberOfTickets(intent.getNumberOfTickets());
        }

        // Cập nhật busType
        if (intent.getBusType() != null) {
            context.setBusType(intent.getBusType());
        }

        // Cập nhật price range
        if (intent.getPriceMin() != null) {
            context.setPriceMin(intent.getPriceMin());
        }
        if (intent.getPriceMax() != null) {
            context.setPriceMax(intent.getPriceMax());
        }

        // Lưu tin nhắn cuối cùng
        context.setLastUserMessage(userMessage);

        return contextRepository.save(context);
    }

    /**
     * Merge context hiện tại với intent mới
     * Trả về SearchIntentDTO hoàn chỉnh
     */
    public SearchIntentDTO mergeContextWithIntent(String userEmail, SearchIntentDTO newIntent) {
        ConversationContext context = getOrCreateContext(userEmail);

        log.info("Merging context for user: {}", userEmail);
        log.info("Context - Departure: {}, Destination: {}, Date: {}", 
            context.getDeparture(), context.getDestination(), context.getDepartureDate());
        log.info("New Intent - Departure: {}, Destination: {}, Date: {}", 
            newIntent.getDeparture(), newIntent.getDestination(), newIntent.getDepartureDate());

        // Tạo intent mới bằng cách merge context và intent mới
        SearchIntentDTO mergedIntent = SearchIntentDTO.builder()
            // Intent type: ưu tiên intent mới, fallback về context
            .intentType(
                (newIntent.getIntentType() != null && !newIntent.getIntentType().equals("GENERAL_QUESTION")) 
                    ? newIntent.getIntentType() 
                    : context.getCurrentIntent()
            )
            // Departure: ưu tiên intent mới, fallback về context
            .departure(newIntent.getDeparture() != null ? newIntent.getDeparture() : context.getDeparture())
            // Destination: ưu tiên intent mới, fallback về context
            .destination(newIntent.getDestination() != null ? newIntent.getDestination() : context.getDestination())
            // Departure date: ưu tiên intent mới, fallback về context
            .departureDate(newIntent.getDepartureDate() != null ? newIntent.getDepartureDate() : context.getDepartureDate())
            // Number of tickets: ưu tiên intent mới, fallback về context
            .numberOfTickets(newIntent.getNumberOfTickets() != null ? newIntent.getNumberOfTickets() : context.getNumberOfTickets())
            // Bus type: ưu tiên intent mới, fallback về context
            .busType(newIntent.getBusType() != null ? newIntent.getBusType() : context.getBusType())
            // Price range: ưu tiên intent mới, fallback về context
            .priceMin(newIntent.getPriceMin() != null ? newIntent.getPriceMin() : context.getPriceMin())
            .priceMax(newIntent.getPriceMax() != null ? newIntent.getPriceMax() : context.getPriceMax())
            // Giữ nguyên các trường khác
            .confidence(newIntent.getConfidence())
            .additionalInfo(newIntent.getAdditionalInfo())
            .build();

        log.info("Merged Intent - Departure: {}, Destination: {}, Date: {}", 
            mergedIntent.getDeparture(), mergedIntent.getDestination(), mergedIntent.getDepartureDate());

        return mergedIntent;
    }

    /**
     * Xóa context của user (reset hội thoại)
     */
    @Transactional
    public void clearContext(String userEmail) {
        Optional<ConversationContext> context = 
            contextRepository.findFirstByUserEmailOrderByUpdatedAtDesc(userEmail);

        context.ifPresent(c -> {
            log.info("Clearing context for user: {}", userEmail);
            contextRepository.delete(c);
        });
    }

    /**
     * Tự động xóa các context đã hết hạn (chạy mỗi giờ)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanExpiredContexts() {
        log.info("Cleaning expired conversation contexts...");
        contextRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}

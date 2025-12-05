package com.busify.project.trip.scheduler;

import com.busify.project.trip.entity.Trip;
import com.busify.project.trip.enums.TripStatus;
import com.busify.project.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripStatusScheduler {

    private final TripRepository tripRepository;
    
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tự động hủy các chuyến đi đã quá thời gian khởi hành
     * Chạy mỗi 5 phút
     */
    @Scheduled(fixedRate = 60000) // 5 phút = 300000ms
    @Transactional
    public void autoCancelExpiredTrips() {
        try {
            // Lấy thời gian hiện tại theo giờ Việt Nam
            ZonedDateTime nowVN = ZonedDateTime.now(VIETNAM_ZONE);
            
            // QUAN TRỌNG: DB lưu giờ VN nhưng Java/Hibernate đọc ra và cộng thêm 7 tiếng (convert sang UTC)
            // Ví dụ: DB lưu 19:00 VN -> Java đọc ra 02:00 UTC (ngày hôm sau)
            // Nên để so sánh đúng, ta cũng cần cộng 7 tiếng vào thời gian hiện tại
            Instant nowForCompare = nowVN.toInstant().plusSeconds(7 * 60 * 60); // Cộng 7 tiếng
            
            // log.info("🔄 [TripStatusScheduler] Running auto-cancel check");
            // log.info("   - Vietnam time now: {}", nowVN);
            // log.info("   - Instant for compare (adjusted +7h): {}", nowForCompare);

            // Tìm các chuyến đi có status on_sell hoặc scheduled mà đã quá thời gian khởi hành
            List<Trip> expiredTrips = tripRepository.findExpiredTrips(nowForCompare);

            // log.info("🔍 [TripStatusScheduler] Query result: found {} expired trips", expiredTrips.size());

            // Debug: Log tất cả trips có status on_sell hoặc scheduled để kiểm tra
            List<Trip> allActiveTrips = tripRepository.findByStatusIn(
                List.of(TripStatus.on_sell, TripStatus.scheduled));
            // log.info("📋 [DEBUG] Total active trips (on_sell/scheduled): {}", allActiveTrips.size());

            // for (Trip trip : allActiveTrips) {
            //     boolean isExpired = trip.getDepartureTime().isBefore(nowForCompare);
            //     log.info("   - Trip ID {}: departure={}, status={}, isExpired={}",
            //             trip.getId(), trip.getDepartureTime(), trip.getStatus(), isExpired);
            // }

            if (!expiredTrips.isEmpty()) {
                // log.info("🔄 Found {} expired trips to cancel", expiredTrips.size());

                for (Trip trip : expiredTrips) {
                    TripStatus oldStatus = trip.getStatus();
                    trip.setStatus(TripStatus.cancelled);
                    tripRepository.save(trip);

                    // log.info("✅ Trip ID {} automatically cancelled (was: {}, departure: {})",
                    //         trip.getId(), oldStatus, trip.getDepartureTime());
                }

                // log.info("✅ Successfully cancelled {} expired trips", expiredTrips.size());
            } else {
                // log.info("✓ [TripStatusScheduler] No expired trips found to cancel");
            }
        } catch (Exception e) {
            // log.error("❌ Error while auto-cancelling expired trips: {}", e.getMessage(), e);
        }
    }
}

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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripStatusScheduler {

    private final TripRepository tripRepository;

    /**
     * Tự động hủy các chuyến đi đã quá thời gian khởi hành
     * Chạy mỗi 10 phút
     */
    @Scheduled(fixedRate = 600000) // 10 phút = 600000ms
    @Transactional
    public void autoCancelExpiredTrips() {
        try {
            Instant now = Instant.now();
            
            // Tìm các chuyến đi có status on_sell hoặc scheduled mà đã quá thời gian khởi hành
            List<Trip> expiredTrips = tripRepository.findExpiredTrips(now);
            
            if (!expiredTrips.isEmpty()) {
                log.info("🔄 Found {} expired trips to cancel", expiredTrips.size());
                
                for (Trip trip : expiredTrips) {
                    TripStatus oldStatus = trip.getStatus();
                    trip.setStatus(TripStatus.cancelled);
                    tripRepository.save(trip);
                    
                    log.info("✅ Trip ID {} automatically cancelled (was: {}, departure: {})", 
                            trip.getId(), oldStatus, trip.getDepartureTime());
                }
                
                log.info("✅ Successfully cancelled {} expired trips", expiredTrips.size());
            } else {
                log.debug("✓ No expired trips found to cancel");
            }
        } catch (Exception e) {
            log.error("❌ Error while auto-cancelling expired trips: {}", e.getMessage(), e);
        }
    }
}

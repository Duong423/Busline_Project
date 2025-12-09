package com.busify.project.trip_seat.services;

import com.busify.project.booking.entity.Bookings;
import com.busify.project.booking.enums.BookingStatus;
import com.busify.project.booking.repository.BookingRepository;
import com.busify.project.payment.entity.Payment;
import com.busify.project.payment.enums.PaymentStatus;
import com.busify.project.payment.repository.PaymentRepository;
import com.busify.project.promotion.entity.Promotion;
import com.busify.project.promotion.service.impl.PromotionServiceImpl;
import com.busify.project.trip_seat.enums.TripSeatStatus;
import com.busify.project.trip_seat.repository.TripSeatRepository;
import com.busify.project.user.entity.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseService {

    private final TripSeatRepository tripSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    // private final PromotionServiceImpl promotionService;

    private final Map<Long, CompletableFuture<Void>> activeReleaseTasks = new ConcurrentHashMap<>();

    /**
     * Recovery mechanism: Scan and release expired seats when server starts
     * This handles cases where server crashed before in-memory tasks could complete
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverExpiredSeatsOnStartup() {
        log.info("Starting recovery scan for expired seat reservations...");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15);

            // Find bookings that are pending payment and older than 15 minutes
            List<Bookings> expiredBookings = bookingRepository.findExpiredPendingBookings(
                    cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant());

            log.info("Found {} expired bookings to process", expiredBookings.size());

            for (Bookings booking : expiredBookings) {
                try {
                    // Release seat và cancel booking
                    releaseExpiredBooking(booking);
                    log.info("Recovered expired booking ID: {}", booking.getId());
                } catch (Exception e) {
                    log.error("Error recovering booking ID: {}", booking.getId(), e);
                }
            }

            log.info("Completed startup recovery scan");
        } catch (Exception e) {
            log.error("Error during startup recovery scan", e);
        }
    }

    /**
     * Periodic backup mechanism: Check for expired bookings every 5 minutes
     * This catches any seats that might have been missed by the in-memory tasks
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // Run every 5 minutes
    @Transactional
    public void periodicExpiredSeatsCheck() {
        log.debug("Running periodic check for expired seat reservations...");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15);
            List<Bookings> expiredBookings = bookingRepository.findExpiredPendingBookings(
                    cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant());

            if (!expiredBookings.isEmpty()) {
                log.info("Periodic check found {} expired bookings", expiredBookings.size());

                for (Bookings booking : expiredBookings) {
                    try {
                        // Only process if not already handled by in-memory task
                        if (!activeReleaseTasks.containsKey(booking.getId())) {
                            releaseExpiredBooking(booking);
                            log.info("Periodic check released booking ID: {}", booking.getId());
                        }
                    } catch (Exception e) {
                        log.error("Error in periodic check for booking ID: {}", booking.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during periodic expired seats check", e);
        }
    }

    @Async("seatReleaseExecutor")
    public CompletableFuture<Void> scheduleRelease(String seatNumber, Long bookingId) {
        log.info("Scheduling seat release for seatId: {}, bookingId: {}", seatNumber, bookingId);

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                // Wait 1 minutes
                Thread.sleep(TimeUnit.MINUTES.toMillis(15));

                // Check if task was cancelled
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Seat release task was cancelled for seatId: {}", seatNumber);
                    return;
                }

                releaseSeatIfNotPaid(seatNumber, bookingId);

            } catch (InterruptedException e) {
                log.info("Seat release task interrupted for seatId: {}", seatNumber);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in seat release task for seatId: {}", seatNumber, e);
            } finally {
                // Remove from active tasks
                activeReleaseTasks.remove(bookingId);
            }
        });

        // Store task để có thể cancel sau này
        activeReleaseTasks.put(bookingId, task);

        return task;
    }

    public void cancelReleaseTask(Long bookingId) {
        CompletableFuture<Void> task = activeReleaseTasks.get(bookingId);
        if (task != null) {
            task.cancel(true);
            activeReleaseTasks.remove(bookingId);
            log.info("Cancelled seat release task for bookingId: {}", bookingId);
        }
    }

    @Transactional
    public void releaseSeatIfNotPaid(String seatNumber, Long bookingId) {
        Bookings booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null)
            return;

        if (booking.getPayment() != null && booking.getPayment().getStatus() != PaymentStatus.pending)
            return;
        
        // Kiểm tra xem booking này có thuộc payment khứ hồi không
        List<Long> relatedBookingIds = findRelatedRoundTripBookings(bookingId);
        
        if (relatedBookingIds.size() > 1) {
            // Đây là booking khứ hồi - cần hủy tất cả các booking liên quan
            log.info("Detected round-trip booking. Will cancel all {} related bookings: {}", 
                relatedBookingIds.size(), relatedBookingIds);
            
            for (Long relatedBookingId : relatedBookingIds) {
                Bookings relatedBooking = bookingRepository.findById(relatedBookingId).orElse(null);
                if (relatedBooking != null && relatedBooking.getStatus() != BookingStatus.canceled_by_operator) {
                    // Hủy release task cho booking liên quan (nếu có)
                    cancelReleaseTask(relatedBookingId);
                    
                    // Release seats và cancel booking
                    releaseSeatsForBooking(relatedBooking);
                    
                    relatedBooking.setStatus(BookingStatus.canceled_by_operator);
                    relatedBooking.setAppliedDiscountCode(null);
                    relatedBooking.setAppliedPromotionId(null);
                    bookingRepository.save(relatedBooking);
                    
                    log.info("Cancelled round-trip booking ID: {}", relatedBookingId);
                }
            }
        } else {
            // Booking đơn lẻ - xử lý như cũ
            tripSeatRepository.findTripSeatBySeatNumberAndTripId(seatNumber, booking.getTrip().getId())
                    .ifPresent(seat -> {
                        log.info("Releasing seat {} for expired booking {}", seat.getId().getSeatNumber(),
                                booking.getId());
                        if (seat.getStatus() == TripSeatStatus.locked) {
                            seat.setStatus(TripSeatStatus.available);
                            seat.setLockingUser(null);
                            seat.setLockedAt(null);
                            tripSeatRepository.save(seat);
                        }
                    });

            booking.setStatus(BookingStatus.canceled_by_operator);
            booking.setAppliedDiscountCode(null);
            booking.setAppliedPromotionId(null);
            bookingRepository.save(booking);
        }
    }
    
    /**
     * Tìm tất cả booking IDs liên quan trong payment khứ hồi
     */
    private List<Long> findRelatedRoundTripBookings(Long bookingId) {
        // Tìm payment chứa bookingId này (qua cột booking_ids)
        List<Payment> payments = paymentRepository.findByBookingIdsContaining(bookingId.toString());
        
        for (Payment payment : payments) {
            if (payment.isRoundTrip()) {
                List<Long> allBookingIds = payment.getBookingIdList();
                if (allBookingIds.contains(bookingId)) {
                    log.info("Found round-trip payment {} (via booking_ids) with bookings: {}", 
                        payment.getPaymentId(), allBookingIds);
                    return allBookingIds;
                }
            }
        }
        
        // Cũng kiểm tra qua cột booking_id (primary booking)
        Payment primaryPayment = paymentRepository.findByBookingId(bookingId);
        if (primaryPayment != null && primaryPayment.isRoundTrip()) {
            List<Long> allBookingIds = primaryPayment.getBookingIdList();
            log.info("Found round-trip payment {} (via booking_id) with bookings: {}", 
                primaryPayment.getPaymentId(), allBookingIds);
            return allBookingIds;
        }
        
        // Không phải khứ hồi, trả về chỉ bookingId hiện tại
        return List.of(bookingId);
    }
    
    /**
     * Release tất cả seats của một booking
     */
    private void releaseSeatsForBooking(Bookings booking) {
        if (booking.getSeatNumber() == null) return;
        
        String[] seatNumbers = booking.getSeatNumber().split(",");
        for (String seatNum : seatNumbers) {
            tripSeatRepository.findTripSeatBySeatNumberAndTripId(
                    seatNum.trim(), booking.getTrip().getId())
                    .ifPresent(seat -> {
                        if (seat.getStatus() == TripSeatStatus.locked) {
                            seat.setStatus(TripSeatStatus.available);
                            seat.setLockingUser(null);
                            seat.setLockedAt(null);
                            tripSeatRepository.save(seat);
                            log.info("Released seat {} for booking {}", 
                                seat.getId().getSeatNumber(), booking.getId());
                        }
                    });
        }
    }

    /**
     * Helper method to release expired booking - used by both recovery mechanisms
     */
    @Transactional
    public void releaseExpiredBooking(Bookings booking) {
        // Validate booking is actually expired and pending
        if (booking.getPayment() != null && booking.getPayment().getStatus() != PaymentStatus.pending) {
            return;
        }

        // Check if booking is older than 15 minutes
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15);
        if (booking.getCreatedAt().isAfter(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant())) {
            return;
        }
        
        // Kiểm tra xem booking này có thuộc payment khứ hồi không
        List<Long> relatedBookingIds = findRelatedRoundTripBookings(booking.getId());
        
        if (relatedBookingIds.size() > 1) {
            // Đây là booking khứ hồi - cần hủy tất cả các booking liên quan
            log.info("Detected round-trip expired booking. Will cancel all {} related bookings: {}", 
                relatedBookingIds.size(), relatedBookingIds);
            
            for (Long relatedBookingId : relatedBookingIds) {
                Bookings relatedBooking = bookingRepository.findById(relatedBookingId).orElse(null);
                if (relatedBooking != null && relatedBooking.getStatus() != BookingStatus.canceled_by_operator) {
                    // Hủy release task cho booking liên quan (nếu có)
                    cancelReleaseTask(relatedBookingId);
                    
                    // Release seats
                    releaseSeatsForBooking(relatedBooking);
                    
                    // Cancel the booking
                    relatedBooking.setStatus(BookingStatus.canceled_by_operator);
                    relatedBooking.setAppliedDiscountCode(null);
                    relatedBooking.setAppliedPromotionId(null);
                    bookingRepository.save(relatedBooking);
                    log.info("Cancelled expired round-trip booking ID: {}", relatedBookingId);
                }
            }
        } else {
            // Booking đơn lẻ - xử lý như cũ
            releaseSeatsForBooking(booking);

            // Cancel the booking
            booking.setStatus(BookingStatus.canceled_by_operator);
            booking.setAppliedDiscountCode(null);
            booking.setAppliedPromotionId(null);
            bookingRepository.save(booking);
            log.info("Cancelled expired booking ID: {}", booking.getId());
        }
    }
}
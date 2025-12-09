package com.busify.project.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.busify.project.payment.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionCode(String transactionCode);

    Optional<Payment> findByPaymentGatewayId(String paymentGatewayId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.booking b WHERE b.Id = :bookingId")
    Payment findByBookingId(Long bookingId);
    
    // Tìm payment theo booking_ids chứa bookingId (với các pattern để tránh match sai)
    // Pattern: booking_ids = "123" hoặc "123,..." hoặc "...,123" hoặc "...,123,..."
    @Query("SELECT p FROM Payment p WHERE p.bookingIds = :bookingIdStr " +
           "OR p.bookingIds LIKE CONCAT(:bookingIdStr, ',%') " +
           "OR p.bookingIds LIKE CONCAT('%,', :bookingIdStr) " +
           "OR p.bookingIds LIKE CONCAT('%,', :bookingIdStr, ',%')")
    List<Payment> findByBookingIdsContaining(@Param("bookingIdStr") String bookingIdStr);
    
    // Tìm payment cho một booking (cả booking_id và booking_ids)
    @Query("SELECT p FROM Payment p LEFT JOIN p.booking b WHERE b.Id = :bookingId " +
           "OR p.bookingIds = :bookingIdStr " +
           "OR p.bookingIds LIKE CONCAT(:bookingIdStr, ',%') " +
           "OR p.bookingIds LIKE CONCAT('%,', :bookingIdStr) " +
           "OR p.bookingIds LIKE CONCAT('%,', :bookingIdStr, ',%')")
    List<Payment> findPaymentsByBookingId(@Param("bookingId") Long bookingId, @Param("bookingIdStr") String bookingIdStr);

    @Override
    @NonNull
    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.booking b LEFT JOIN FETCH b.trip t WHERE p.paymentId = :paymentId")
    Optional<Payment> findById(@NonNull @Param("paymentId") Long id);
    
    // Tìm payment đã completed theo paymentId
    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.booking b LEFT JOIN FETCH b.trip t WHERE p.paymentId = :paymentId AND p.status = 'completed'")
    Optional<Payment> findCompletedById(@Param("paymentId") Long id);
}

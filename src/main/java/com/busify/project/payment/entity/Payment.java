package com.busify.project.payment.entity;

import com.busify.project.booking.entity.Bookings;
import com.busify.project.payment.enums.PaymentMethod;
import com.busify.project.payment.enums.PaymentStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    // Dùng cho trường hợp 1 booking (backward compatible)
    @OneToOne
    @JoinColumn(name = "booking_id", nullable = true)
    private Bookings booking;

    // Dùng cho trường hợp nhiều booking (khứ hồi) - lưu dạng "id1,id2,id3"
    @Column(name = "booking_ids")
    private String bookingIds;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(unique = true)
    private String transactionCode;

    @Column(name = "payment_gateway_id")
    private String paymentGatewayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.pending;

    @Column(name = "paid_at")
    private Instant paidAt;

    // Relationship với Refund
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<com.busify.project.refund.entity.Refund> refunds;

    // Helper method để lấy danh sách booking IDs
    public List<Long> getBookingIdList() {
        if (bookingIds != null && !bookingIds.trim().isEmpty()) {
            return Arrays.stream(bookingIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }
        if (booking != null) {
            return Collections.singletonList(booking.getId());
        }
        return Collections.emptyList();
    }

    // Helper method để set danh sách booking IDs
    public void setBookingIdList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.bookingIds = null;
        } else if (ids.size() == 1) {
            // Nếu chỉ có 1 booking, không cần set bookingIds
            this.bookingIds = null;
        } else {
            this.bookingIds = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }

    // Kiểm tra có phải là round trip (khứ hồi) không
    public boolean isRoundTrip() {
        return bookingIds != null && !bookingIds.trim().isEmpty() && bookingIds.contains(",");
    }
}

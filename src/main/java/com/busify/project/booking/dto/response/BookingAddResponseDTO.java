package com.busify.project.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.busify.project.booking.enums.BookingStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingAddResponseDTO {
    private Long bookingId; // Thêm booking_id
    private String bookingCode; // Thêm booking_code
    private String discountCode;
    private String seatNumber;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private Instant expiresAt; // Thời điểm hết hạn booking (createdAt + 15 phút)
}

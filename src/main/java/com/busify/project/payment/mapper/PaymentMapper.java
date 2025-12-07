package com.busify.project.payment.mapper;

import com.busify.project.payment.dto.response.PaymentResponseDTO;
import com.busify.project.payment.entity.Payment;

import java.util.List;

public class PaymentMapper {

    public static PaymentResponseDTO toResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        // Lấy danh sách tất cả booking IDs
        List<Long> allBookingIds = payment.getBookingIdList();
        
        // Lấy booking ID đầu tiên cho backward compatibility
        Long primaryBookingId = payment.getBooking() != null 
                ? payment.getBooking().getId() 
                : (allBookingIds.isEmpty() ? null : allBookingIds.get(0));

        return PaymentResponseDTO.builder()
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus())
                .paymentUrl(null)
                .bookingId(primaryBookingId)
                .bookingIds(allBookingIds)
                .build();
    }
}

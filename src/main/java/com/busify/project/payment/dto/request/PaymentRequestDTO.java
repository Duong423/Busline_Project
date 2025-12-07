package com.busify.project.payment.dto.request;

import com.busify.project.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentRequestDTO {
    // Dùng cho trường hợp 1 booking (backward compatible)
    @Positive(message = "Booking ID phải là số dương")
    private Long bookingId;
    
    // Dùng cho trường hợp nhiều booking (khứ hồi)
    private List<Long> bookingIds;
    
    @NotNull(message = "Phương thức thanh toán không được null")
    private PaymentMethod paymentMethod;

    public HttpServletRequest getHttpServletRequest() {
        throw new UnsupportedOperationException("Unimplemented method 'getHttpServletRequest'");
    }
    
    // Helper method để lấy tất cả booking IDs
    public List<Long> getAllBookingIds() {
        if (bookingIds != null && !bookingIds.isEmpty()) {
            return bookingIds;
        }
        if (bookingId != null) {
            return List.of(bookingId);
        }
        return List.of();
    }
    
    // Kiểm tra có phải round trip không
    public boolean isRoundTrip() {
        return bookingIds != null && bookingIds.size() > 1;
    }
}

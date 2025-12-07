package com.busify.project.payment.dto.response;

import com.busify.project.payment.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentResponseDTO {
    private Long paymentId;
    
    private PaymentStatus status;
    
    private String paymentUrl;
    
    // Backward compatible - dùng cho 1 booking
    private Long bookingId;
    
    // Dùng cho nhiều booking (khứ hồi)
    private List<Long> bookingIds;
    
    // Kiểm tra có phải round trip không
    public boolean isRoundTrip() {
        return bookingIds != null && bookingIds.size() > 1;
    }
}

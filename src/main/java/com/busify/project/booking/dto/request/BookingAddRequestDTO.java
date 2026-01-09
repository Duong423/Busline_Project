package com.busify.project.booking.dto.request;

import java.math.BigDecimal;

import com.busify.project.booking.enums.SellingMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingAddRequestDTO {

    @NotNull
    private Long tripId;

    private String guestFullName;
    private String guestEmail;
    private String guestPhone;
    private String guestAddress;

    private String discountCode;

    private Long promotionId;

    private SellingMethod sellingMethod;

    @NotNull
    @Size(min = 3)
    private String seatNumber;

    @NotNull
    @PositiveOrZero
    private BigDecimal totalAmount;

    /**
     * Validates booking constraints for fairness
     * - Maximum 10 seats per booking to prevent hoarding
     * - Ensures fair access for all customers
     */
    public void validate() {
        if (seatNumber != null && !seatNumber.trim().isEmpty()) {
            String[] seats = seatNumber.split(",");
            if (seats.length > 10) {
                throw new IllegalArgumentException(
                    "Để đảm bảo công bằng, mỗi lần đặt vé chỉ được phép đặt tối đa 10 ghế. "
                    + "Bạn đang cố đặt " + seats.length + " ghế. "
                    + "Vui lòng chia nhỏ đơn đặt vé hoặc liên hệ hotline để hỗ trợ.");
            }
        }
    }
}

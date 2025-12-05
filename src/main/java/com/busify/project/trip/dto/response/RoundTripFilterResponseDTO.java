package com.busify.project.trip.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoundTripFilterResponseDTO {
    // Thông tin chuyến đi (chiều đi)
    private FilterResponseDTO outboundTrips;
    
    // Thông tin chuyến về (chiều về) - null nếu không phải khứ hồi
    private FilterResponseDTO returnTrips;
    
    // Có phải tìm kiếm khứ hồi không
    private Boolean isRoundTrip;
    
    // Thông báo nếu không tìm thấy chuyến khứ hồi
    private String returnTripMessage;
}

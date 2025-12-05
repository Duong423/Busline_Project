package com.busify.project.trip.dto.request;

import java.time.Instant;

import com.busify.project.common.config.VietnamInstantDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RoundTripFilterRequestDTO {
    private Long startLocation;
    private Long endLocation;
    
    @JsonDeserialize(using = VietnamInstantDeserializer.class)
    private Instant departureDate;
    
    // Khứ hồi - optional
    private Boolean isRoundTrip = false;
    
    @JsonDeserialize(using = VietnamInstantDeserializer.class)
    private Instant returnDate;
    
    private String[] busModels;
    
    @JsonDeserialize(using = VietnamInstantDeserializer.class)
    private Instant untilTime;
    
    @Pattern(regexp = "^[a-zA-Z0-9_/]+$", message = "Time zone must be in the format 'region/city'")
    private String timeZone = "Asia/Ho_Chi_Minh";
    
    @Pattern(regexp = "^[a-zA-Z0-9 ]+$", message = "Operator name can only contain alphanumeric characters and spaces")
    private String operatorName;
    
    private String[] amenities;
    private int availableSeats;
    private String sortBy = "departureTime";
    private String sortDirection = "ASC";
    private String sortBySecondary;
    private String sortDirectionSecondary;
}

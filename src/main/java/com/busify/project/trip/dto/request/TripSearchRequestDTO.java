package com.busify.project.trip.dto.request;

import com.busify.project.common.config.VietnamInstantDeserializer;
import com.busify.project.trip.enums.TripStatus;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import java.time.Instant;

@Data
public class TripSearchRequestDTO {
    @JsonDeserialize(using = VietnamInstantDeserializer.class)
    private Instant departureDate;
    
    @JsonDeserialize(using = VietnamInstantDeserializer.class)
    private Instant untilTime;
    private Integer availableSeats;
    private Long startLocation;
    private Long endLocation;
    private TripStatus status;
}
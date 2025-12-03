package com.busify.project.chat.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.busify.project.chat.dto.SearchIntentDTO;
import com.busify.project.chat.dto.TripSearchResultDTO;
import com.busify.project.location.entity.Location;
import com.busify.project.location.repository.LocationRepository;
import com.busify.project.trip.dto.response.TripFilterResponseDTO;
import com.busify.project.trip.enums.TripStatus;
import com.busify.project.trip.service.impl.TripServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service tìm kiếm chuyến đi dựa trên ý định từ chat
 * Sử dụng API thật từ TripServiceImpl
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripSearchService {

    private final TripServiceImpl tripService;
    private final LocationRepository locationRepository;

    /**
     * Tìm kiếm chuyến đi dựa trên ý định được trích xuất
     */
    public List<TripSearchResultDTO> searchTrips(SearchIntentDTO intent) {
        try {
            log.info("Searching trips with intent: {}", intent);

            // TODO: Implement actual database search
            // Ví dụ:
            // List<Trip> trips = tripRepository.findBySearchCriteria(
            //     intent.getDeparture(),
            //     intent.getDestination(),
            //     intent.getDepartureDate(),
            //     intent.getBusType()
            // );

            // Mock data for demonstration
            List<TripSearchResultDTO> results = searchTripsFromDatabase(intent);

            // Filter và sort kết quả
            results = filterAndSortResults(results, intent);

            log.info("Found {} trips matching the criteria", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching trips", e);
            return new ArrayList<>();
        }
    }

    /**
     * Tìm kiếm từ database sử dụng API thật
     */
    private List<TripSearchResultDTO> searchTripsFromDatabase(SearchIntentDTO intent) {
        try {
            log.info("🔍 Searching trips from database with intent: {}", intent);
            
            // Tìm location IDs từ tên địa điểm
            Long startLocationId = null;
            Long endLocationId = null;
            
            if (intent.getDeparture() != null) {
                List<Location> locations = locationRepository.searchByNameOrCity(intent.getDeparture());
                log.info("📍 Found {} locations for departure '{}': {}", 
                    locations.size(), intent.getDeparture(), 
                    locations.stream().map(l -> l.getId() + ":" + l.getName() + "(" + l.getCity() + ")").toList());
                startLocationId = locations.isEmpty() ? null : locations.get(0).getId();
                
                if (startLocationId == null) {
                    log.warn("⚠️ No location found for departure: {}", intent.getDeparture());
                }
            }
            
            if (intent.getDestination() != null) {
                List<Location> locations = locationRepository.searchByNameOrCity(intent.getDestination());
                log.info("📍 Found {} locations for destination '{}': {}", 
                    locations.size(), intent.getDestination(),
                    locations.stream().map(l -> l.getId() + ":" + l.getName() + "(" + l.getCity() + ")").toList());
                endLocationId = locations.isEmpty() ? null : locations.get(0).getId();
                
                if (endLocationId == null) {
                    log.warn("⚠️ No location found for destination: {}", intent.getDestination());
                }
            }
            
            // ✅ Convert ngày tìm kiếm sang UTC range (00:00:00 đến 23:59:59 UTC của ngày đó)
            // VD: User search "10/11" → Query trips có departure_time trong khoảng [2025-11-10 00:00:00 UTC, 2025-11-11 00:00:00 UTC)
            final Instant from;
            final Instant until;
            
            if (intent.getDepartureDate() != null) {
                // Start of day UTC (00:00:00)
                from = intent.getDepartureDate()
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant();
                
                // Start of next day UTC (00:00:00 ngày mai)
                until = intent.getDepartureDate()
                    .plusDays(1)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant();
                
                log.info("📅 Searching trips with departure_time (UTC) between {} and {} (for date: {})", 
                    from, until, intent.getDepartureDate());
            } else {
                from = null;
                until = null;
                log.info("📅 No departure date specified, searching all future trips");
            }
            
            // Log search parameters
            log.info("🔎 Search parameters: startLocationId={}, endLocationId={}, from={}, until={}, seats={}, status={}", 
                startLocationId, endLocationId, from, until, intent.getNumberOfTickets(), "ALL (null)");
            
            // Gọi service để search với khoảng thời gian UTC
            List<TripFilterResponseDTO> trips = tripService.searchTrips(
                from,
                until,
                intent.getNumberOfTickets(),
                startLocationId,
                endLocationId,
                null // Lấy TẤT CẢ status
            );
            
            log.info("✅ Found {} trips from database", trips.size());
            
            // KHÔNG cần filter lại theo LocalDate vì đã query đúng khoảng UTC
            if (intent.getDepartureDate() != null && !trips.isEmpty()) {
                log.info("📅 Trips already filtered by database query (no re-filtering needed)");
                
                // Log example trip for debugging
                if (!trips.isEmpty()) {
                    TripFilterResponseDTO firstTrip = trips.get(0);
                    log.info("🔍 Example trip: id={}, departure_time={}, status={}", 
                        firstTrip.getTrip_id(), 
                        firstTrip.getDeparture_time(),
                        firstTrip.getStatus());
                }
            }
            
            // ✅ Lọc bỏ các chuyến có status ARRIVED hoặc CANCELLED
            trips = trips.stream()
                .filter(trip -> {
                    TripStatus status = trip.getStatus();
                    boolean isValidStatus = status != null && 
                        status != TripStatus.arrived && 
                        status != TripStatus.cancelled;
                    
                    if (!isValidStatus) {
                        log.info("⚠️ Filtered out trip {} with status: {}", trip.getTrip_id(), status);
                    }
                    
                    return isValidStatus;
                })
                .collect(Collectors.toList());
            
            log.info("✅ After filtering arrived/cancelled trips: {} trips remaining", trips.size());
            
            // Convert sang TripSearchResultDTO
            return trips.stream()
                .map(this::convertToSearchResultDTO)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error searching trips from database", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Convert TripFilterResponseDTO sang TripSearchResultDTO
     */
    private TripSearchResultDTO convertToSearchResultDTO(TripFilterResponseDTO trip) {
        return TripSearchResultDTO.builder()
            .tripId(trip.getTrip_id())
            .routeName(trip.getRoute() != null ? 
                trip.getRoute().getStart_location() + " - " + trip.getRoute().getEnd_location() : "")
            .departureLocation(trip.getRoute() != null ? trip.getRoute().getStart_location() : "")
            .arrivalLocation(trip.getRoute() != null ? trip.getRoute().getEnd_location() : "")
            .departureTime(trip.getDeparture_time() != null ? 
                trip.getDeparture_time().atZone(ZoneId.systemDefault()).toLocalDateTime() : null)
            .arrivalTime(trip.getArrival_time() != null ? 
                trip.getArrival_time().atZone(ZoneId.systemDefault()).toLocalDateTime() : null)
            .price(trip.getPrice_per_seat() != null ? trip.getPrice_per_seat().doubleValue() : 0.0)
            .availableSeats(trip.getAvailable_seats() != null ? trip.getAvailable_seats() : 0)
            .busType(extractBusType(trip.getAmenities()))
            .busPlate(trip.getOperator_name()) // Tạm dùng operator name
            .amenities(extractAmenities(trip.getAmenities()))
            .rating(trip.getAverage_rating())
            .hasPromotion(false) // TODO: Implement promotion check
            .discountedPrice(null) // TODO: Calculate discounted price
            .imageUrl(trip.getOperator_avatar())
            .build();
    }
    
    /**
     * Trích xuất loại xe từ amenities
     */
    private String extractBusType(Map<String, Object> amenities) {
        if (amenities == null) return "Xe thường";
        
        // Kiểm tra các tiện nghi để xác định loại xe
        boolean hasWifi = amenities.containsKey("wifi") || amenities.containsKey("WiFi");
        boolean hasAC = amenities.containsKey("air_conditioning") || amenities.containsKey("điều hòa");
        boolean hasTV = amenities.containsKey("tv") || amenities.containsKey("TV");
        
        if (hasWifi && hasAC && hasTV) {
            return "VIP";
        } else if (hasAC) {
            return "Xe thường";
        } else {
            return "Xe thường";
        }
    }
    
    /**
     * Trích xuất danh sách tiện nghi
     */
    private List<String> extractAmenities(Map<String, Object> amenities) {
        if (amenities == null || amenities.isEmpty()) {
            return List.of("Điều hòa");
        }
        
        return amenities.keySet().stream()
            .map(key -> {
                // Chuyển đổi key sang tiếng Việt
                switch (key.toLowerCase()) {
                    case "wifi": return "WiFi";
                    case "air_conditioning": return "Điều hòa";
                    case "tv": return "TV";
                    case "water": return "Nước uống";
                    case "blanket": return "Chăn gối";
                    case "usb_charging": return "Sạc điện thoại";
                    default: return key;
                }
            })
            .collect(Collectors.toList());
    }

    /**
     * Filter và sắp xếp kết quả
     */
    private List<TripSearchResultDTO> filterAndSortResults(
            List<TripSearchResultDTO> results, SearchIntentDTO intent) {
        
        return results.stream()
            // Filter theo giá nếu có
            .filter(trip -> {
                if (intent.getPriceMin() != null && trip.getPrice() < intent.getPriceMin()) {
                    return false;
                }
                if (intent.getPriceMax() != null && trip.getPrice() > intent.getPriceMax()) {
                    return false;
                }
                return true;
            })
            // Filter theo số ghế trống
            .filter(trip -> {
                if (intent.getNumberOfTickets() != null) {
                    return trip.getAvailableSeats() >= intent.getNumberOfTickets();
                }
                return true;
            })
            // Sort theo giá hoặc rating
            .sorted((t1, t2) -> {
                // Ưu tiên xe có promotion
                if (t1.getHasPromotion() && !t2.getHasPromotion()) return -1;
                if (!t1.getHasPromotion() && t2.getHasPromotion()) return 1;
                
                // Sort theo giá thấp nhất
                return Double.compare(
                    t1.getDiscountedPrice() != null ? t1.getDiscountedPrice() : t1.getPrice(),
                    t2.getDiscountedPrice() != null ? t2.getDiscountedPrice() : t2.getPrice()
                );
            })
            .limit(10) // Giới hạn 10 kết quả
            .collect(Collectors.toList());
    }

    /**
     * Convert Trip entity to DTO (implement based on your entity)
     */
    /*
    private TripSearchResultDTO convertToDTO(Trip trip) {
        return TripSearchResultDTO.builder()
            .tripId(trip.getId())
            .routeName(trip.getRoute().getName())
            .departureLocation(trip.getRoute().getDepartureLocation())
            .arrivalLocation(trip.getRoute().getArrivalLocation())
            .departureTime(trip.getDepartureTime())
            .arrivalTime(trip.getArrivalTime())
            .price(trip.getPrice())
            .availableSeats(trip.getAvailableSeats())
            .busType(trip.getBus().getType())
            .busPlate(trip.getBus().getPlateNumber())
            .amenities(trip.getBus().getAmenities())
            .rating(trip.getAverageRating())
            .hasPromotion(trip.getPromotion() != null)
            .discountedPrice(trip.getDiscountedPrice())
            .imageUrl(trip.getBus().getImageUrl())
            .build();
    }
    */

    /**
     * Mock data for testing
     */
    /**
     * Tìm kiếm theo ID
     */
    public TripSearchResultDTO getTripById(Long tripId) {
        // TODO: Implement actual database lookup
        // return convertToDTO(tripRepository.findById(tripId).orElse(null));
        
        // Mock data
        return TripSearchResultDTO.builder()
            .tripId(tripId)
            .routeName("Hà Nội - Đà Nẵng")
            .departureLocation("Hà Nội")
            .arrivalLocation("Đà Nẵng")
            .departureTime(LocalDate.now().atTime(8, 0))
            .arrivalTime(LocalDate.now().atTime(14, 30))
            .price(350000.0)
            .availableSeats(12)
            .busType("VIP")
            .busPlate("29A-12345")
            .amenities(List.of("WiFi", "Điều hòa", "Nước uống miễn phí"))
            .rating(4.5)
            .hasPromotion(true)
            .discountedPrice(315000.0)
            .imageUrl("/images/bus-vip.jpg")
            .build();
    }
}


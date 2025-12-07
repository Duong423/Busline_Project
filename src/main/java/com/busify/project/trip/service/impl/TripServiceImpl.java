package com.busify.project.trip.service.impl;

import com.busify.project.booking.enums.BookingStatus;
import com.busify.project.booking.repository.BookingRepository;
import com.busify.project.bus_operator.repository.BusOperatorRepository;
import com.busify.project.review.repository.ReviewRepository;
import com.busify.project.common.utils.JwtUtils;
import com.busify.project.audit_log.entity.AuditLog;
import com.busify.project.audit_log.service.AuditLogService;
import com.busify.project.trip.dto.response.*;
import com.busify.project.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.busify.project.user.entity.User;
import com.busify.project.location.enums.LocationRegion;
import com.busify.project.trip.entity.Trip;
import com.busify.project.route.dto.response.RouteResponse;
import com.busify.project.seat_layout.entity.SeatLayout;
import com.busify.project.seat_layout.repository.SeatLayoutRepository;
import com.busify.project.trip.dto.request.RoundTripFilterRequestDTO;
import com.busify.project.trip.dto.request.TripFilterRequestDTO;
import com.busify.project.trip.dto.request.TripUpdateStatusRequest;
import com.busify.project.trip.enums.TripStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

import com.busify.project.trip.exception.TripOperationException;
import com.busify.project.trip.mapper.TripMapper;
import com.busify.project.trip.repository.TripRepository;
import com.busify.project.trip.service.TripService;
import com.busify.project.ticket.dto.response.TicketSeatStatusReponse;
import com.busify.project.ticket.service.TicketService;
import com.busify.project.booking.service.BookingService;
import com.busify.project.bus.dto.response.BusLayoutResponseDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TripServiceImpl implements TripService {

    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private BusOperatorRepository busOperatorRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketService ticketService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private SeatLayoutRepository seatLayoutRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<TripFilterResponseDTO> getAllTrips() {
        return tripRepository.findAll()
                .stream()
                .filter(trip -> trip.getStatus() != TripStatus.cancelled)
                .map(trip -> TripMapper.toDTO(trip, getAverageRating(trip.getId()), bookingRepository))
                .collect(Collectors.toList());
    }

    public List<TripFilterResponseDTO> getTripsForCurrentDriver() {
        // Lấy thông tin user hiện tại từ JWT
        Optional<String> currentUserEmail = jwtUtils.getCurrentUserLogin();


        if (currentUserEmail.isEmpty()) {
            throw new IllegalStateException("Người dùng chưa đăng nhập");
        }

        // Tìm user theo email
        User currentUser = userRepository.findByEmailIgnoreCase(currentUserEmail.get())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin người dùng"));

        // Lấy thời gian hiện tại để lọc chuyến đi
        Instant currentTime = Instant.now();

        // Lấy trips của driver hiện tại và chỉ hiển thị những chuyến đi chưa khởi hành
        List<TripFilterResponseDTO> result = tripRepository
                .findUpcomingTripsByDriverId(currentUser.getId(), currentTime)
                .stream()
                .filter(trip -> trip.getStatus() != TripStatus.cancelled)
                .map(trip -> TripMapper.toDTO(trip, getAverageRating(trip.getId()), bookingRepository))
                .collect(Collectors.toList());

       

        return result;
    }

    @Override
    public FilterResponseDTO filterTrips(TripFilterRequestDTO filter, int page, int size) {
        Logger logger = Logger.getLogger(TripServiceImpl.class.getName());
        logger.info(filter.toString());

        List<String> amenitiesList = filter.getAmenities() != null ? Arrays.asList(filter.getAmenities()) : null;
        List<String> busModelsList = filter.getBusModels() != null ? Arrays.asList(filter.getBusModels()) : null;

        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "departureTime";
        String sortDirection = filter.getSortDirection() != null ? filter.getSortDirection() : "ASC";

        // Map sortBy to entity field
        if ("price".equals(sortBy)) {
            sortBy = "pricePerSeat";
        } else {
            sortBy = "departureTime"; // default
        }

        Sort sort = "DESC".equalsIgnoreCase(sortDirection) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        // Add secondary sort if provided
        if (filter.getSortBySecondary() != null) {
            String secondarySortBy = filter.getSortBySecondary();
            if ("price".equals(secondarySortBy)) {
                secondarySortBy = "pricePerSeat";
            } else {
                secondarySortBy = "departureTime";
            }
            String secondaryDirection = filter.getSortDirectionSecondary() != null ? filter.getSortDirectionSecondary()
                    : "ASC";
            Sort secondarySort = "DESC".equalsIgnoreCase(secondaryDirection) ? Sort.by(secondarySortBy).descending()
                    : Sort.by(secondarySortBy).ascending();
            sort = sort.and(secondarySort);
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        // Tính untilTime để lọc đúng trong ngày (từ 00:00:00 đến 23:59:59 của ngày đó)
        // Nếu departureDate được truyền và untilTime không được truyền,
        // thì tự động set untilTime = departureDate + 1 ngày để chỉ lấy trip trong ngày
        Instant effectiveUntilTime = filter.getUntilTime();
        if (filter.getDepartureDate() != null && effectiveUntilTime == null) {
            // Cộng thêm 1 ngày vào departureDate để lấy cuối ngày
            effectiveUntilTime = filter.getDepartureDate().plus(java.time.Duration.ofDays(1));
            logger.info("Auto-calculated untilTime for same-day filtering: " + effectiveUntilTime);
        }

        Page<Trip> tripPage = tripRepository.filterTrips(
                filter.getOperatorName(),
                effectiveUntilTime,
                filter.getDepartureDate(),
                filter.getStartLocation(),
                filter.getEndLocation(),
                TripStatus.on_sell,
                filter.getAvailableSeats(),
                pageable);

        List<Trip> trips = tripPage.getContent();

        List<TripFilterResponseDTO> tripDTOs = trips.stream()
                .filter(trip -> {
                    final Map<String, Object> tripMenities = trip.getBus().getAmenities();
                    tripMenities.forEach((key, value) -> {
                        if (amenitiesList != null && amenitiesList.contains(key) && value.equals(true)) {
                            tripMenities.put(key, value);
                        }
                    });
                    return !tripMenities.isEmpty();
                })
                .filter(trip -> {
                    if (busModelsList != null && !busModelsList.isEmpty()) {
                        return busModelsList.contains(trip.getBus().getModel().getName());
                    }
                    return true;
                })
                .map(trip -> TripMapper.toDTO(trip, getAverageRating(trip.getId()), bookingRepository))
                .collect(Collectors.toList());

        if (tripDTOs.isEmpty()) {
            return new FilterResponseDTO(
                    page, size, tripPage.getTotalPages(),
                    tripPage.isFirst(), tripPage.isLast(), new ArrayList<>());
        }

        return new FilterResponseDTO(page, size, tripPage.getTotalPages(),
                tripPage.isFirst(), tripPage.isLast(), tripDTOs);
    }

    public List<TripFilterResponseDTO> searchTrips(Instant departureDate, Instant untilTime, Integer availableSeats,
            Long startLocation, Long endLocation, TripStatus status) {
        log.info("🔍 TripServiceImpl.searchTrips() called with: departureDate={}, untilTime={}, seats={}, startLoc={}, endLoc={}, status={}", 
            departureDate, untilTime, availableSeats, startLocation, endLocation, status);
        
        List<Trip> trips = tripRepository.searchTrips(departureDate, untilTime, startLocation, endLocation, status,
                availableSeats);
        
        log.info("📊 TripRepository returned {} trips", trips.size());
        if (!trips.isEmpty()) {
            log.info("📋 First trip: id={}, route={}, departure={}", 
                trips.get(0).getId(), 
                trips.get(0).getRoute().getName(),
                trips.get(0).getDepartureTime());
        }
        
        return trips.stream()
                .filter(trip -> trip.getStatus() != TripStatus.cancelled)
                .map(trip -> TripMapper.toDTO(trip, getAverageRating(trip.getId()), bookingRepository))
                .collect(Collectors.toList());
    }

    private Double getAverageRating(Long tripId) {
        Double rating = reviewRepository.findAverageRatingByTripId(tripId);
        if (rating == null)
            return 0.0;

        return Math.round(rating * 10.0) / 10.0;
    }

    public List<TripResponse> findTopUpcomingTripByOperator() {
        List<TopOperatorRatingDTO> operators = busOperatorRepository.findTopRatedOperatorId(PageRequest.of(0, 5));

        List<Trip> trips = new ArrayList<>();

        // Map để lưu rating của mỗi operator
        Map<Long, Double> operatorRatings = operators.stream()
                .collect(Collectors.toMap(TopOperatorRatingDTO::getOperatorId, TopOperatorRatingDTO::getAverageRating));

        for (TopOperatorRatingDTO operator : operators) {
            Trip trip = tripRepository.findUpcomingTripsByOperator(operator.getOperatorId(), Instant.now());
            if (trip != null && trip.getStatus() != TripStatus.cancelled) {
                trips.add(trip);
            }
        }
        List<TripResponse> tripsResponses = trips.stream().limit(4).map(trip -> TripResponse
                .builder()
                .trip_id(trip.getId())
                .operator_name(trip.getBus().getOperator().getName()).route(
                        RouteResponse.builder()
                                .start_location(trip.getRoute().getStartLocation().getName())
                                .end_location(trip.getRoute().getEndLocation().getName())
                                .default_duration_minutes(trip.getRoute().getDefaultDurationMinutes())
                                .build())
                .arrival_time(trip.getEstimatedArrivalTime())
                .price_per_seat(trip.getPricePerSeat())
                .available_seats((int) (trip.getBus().getTotalSeats() - trip.getBookings().stream()
                        .filter(b -> b.getStatus() != BookingStatus.canceled_by_user
                                && b.getStatus() != BookingStatus.canceled_by_operator)
                        .count()))
                .departure_time(trip.getDepartureTime())
                .status(trip.getStatus())
                .average_rating(operatorRatings.get(trip.getBus().getOperator().getId()))
                .build()).collect(Collectors.toList());

        if (tripsResponses.isEmpty()) {
            return new ArrayList<>();
        }
        return tripsResponses;
    }

    @Override
    public Map<String, Object> getTripDetailById(Long tripId) {
        try {
            // Kiểm tra xem chuyến đi có tồn tại không
            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi với ID: " + tripId));
            
            // Kiểm tra trạng thái chuyến đi
            if (trip.getStatus() == TripStatus.cancelled) {
                throw new IllegalStateException("Chuyến đi này đã bị hủy và không thể xem chi tiết");
            }
            
            // Kiểm tra nếu chuyến đi đã đến nơi
            if (trip.getStatus() == TripStatus.arrived) {
                throw new IllegalStateException("Chuyến đi này đã hoàn thành và không thể truy cập");
            }
            
            // Kiểm tra nếu chuyến đi đã quá thời gian khởi hành nhưng chưa được cập nhật status
            if (trip.getDepartureTime().isBefore(Instant.now()) && 
                (trip.getStatus() == TripStatus.on_sell || trip.getStatus() == TripStatus.scheduled)) {
                throw new IllegalStateException("Chuyến đi này đã quá thời gian khởi hành");
            }
            
            // get trip detail by ID
            TripDetailResponse tripDetail = tripRepository.findTripDetailById(tripId);
            // get trip stop by ID
            List<TripStopResponse> tripStops = tripRepository.findTripStopsById(tripId);
            // lấy danh sách hình ảnh bus
            List<BusImageResponse> busImages = tripRepository.findBusImagesByBusId(tripDetail.getBusId());
            // mapper to Map<String, Object> using mapper toTripDetail
            return TripMapper.toTripDetail(tripDetail, tripStops, busImages);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw TripOperationException.processingFailed(e);
        }

    }

    @Override
    public List<TripRouteResponse> getTripRouteById(Long routeId) {
        try {
            return tripRepository.findUpcomingTripsByRoute(routeId);
        } catch (Exception e) {
            throw TripOperationException.processingFailed(e);

        }
    }

    @Override
    public List<TripFilterResponseDTO> getTripRouteByIdExcludingTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        Long routeId = trip.getRoute().getId();
        final List<TripFilterResponseDTO> similarTrips = tripRepository.findUpcomingTripsByRouteExcludingTrip(routeId,
                tripId).stream()
                .filter(t -> t.getStatus() != TripStatus.cancelled)
                .map(t -> TripMapper.toDTO(t, getAverageRating(t.getId()), bookingRepository))
                .collect(Collectors.toList());
        System.out.println("Similar trips found: " + similarTrips.get(0));
        return similarTrips;
    }

    @Override
    public List<TripStopResponse> getTripStopsById(Long tripId) {
        try {
            return tripRepository.findTripStopsById(tripId);
        } catch (Exception e) {
            throw TripOperationException.processingFailed(e);
        }
    }

    public List<Map<String, Object>> getNextTripsOfOperator(Long operatorId) {
        List<NextTripsOfOperatorResponseDTO> nextTrips = tripRepository.findNextTripsByOperator(operatorId);
        if (nextTrips.isEmpty()) {
            return List.of(Map.of("message", "No upcoming trips found for this operator."));
        }

        return nextTrips.stream().map(TripMapper::toNextTripsOfOperatorResponse).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> updateTripStatus(Long tripId, TripUpdateStatusRequest request) {
        try {
            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi với ID: " + tripId));

            TripStatus oldStatus = trip.getStatus();

            // Kiểm tra logic chuyển đổi trạng thái
            validateStatusTransition(trip.getStatus(), request.getStatus());

            // Cập nhật trạng thái
            trip.setStatus(request.getStatus());
            tripRepository.save(trip);

            // Audit log for trip status update
            User currentUser = getCurrentUser();
            AuditLog auditLog = new AuditLog();
            auditLog.setAction("UPDATE");
            auditLog.setTargetEntity("TRIP_STATUS");
            auditLog.setTargetId(tripId);
            auditLog.setDetails(String.format("{\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"reason\":\"%s\"}",
                    oldStatus, request.getStatus(), request.getReason()));
            auditLog.setUser(currentUser);
            auditLogService.save(auditLog);

            // Logic tự động hủy vé khi trip chuyển sang departed
            int cancelledTickets = 0;
            if (request.getStatus() == TripStatus.departed) {
                System.out.println("=== DEBUG: Trip status changed to departed, calling auto-cancel tickets ===");
                cancelledTickets = ticketService.autoCancelValidTicketsWhenTripDeparted(tripId);
                System.out.println("Auto-cancelled tickets count: " + cancelledTickets);
            }

            // Logic tự động hoàn thành booking khi trip chuyển sang arrived
            int completedBookings = 0;
            if (request.getStatus() == TripStatus.arrived) {
                System.out.println("=== DEBUG: Trip status changed to arrived, calling auto-complete bookings ===");
                completedBookings = bookingService.markBookingsAsCompletedWhenTripArrived(tripId);
                System.out.println("Auto-completed bookings count: " + completedBookings);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cập nhật trạng thái chuyến đi thành công");
            response.put("tripId", tripId);
            response.put("oldStatus", oldStatus);
            response.put("newStatus", request.getStatus());
            response.put("reason", request.getReason());

            // Thêm thông tin về việc tự động hủy vé
            if (cancelledTickets > 0) {
                response.put("autoCancelledTickets", cancelledTickets);
                response.put("autoCancelMessage",
                        String.format("Đã tự động hủy %d vé chưa sử dụng do chuyến đi đã khởi hành", cancelledTickets));
            }

            // Thêm thông tin về việc tự động hoàn thành booking
            if (completedBookings > 0) {
                response.put("autoCompletedBookings", completedBookings);
                response.put("autoCompleteMessage",
                        String.format("Đã tự động hoàn thành %d booking do chuyến đi đã đến nơi", completedBookings));
            }

            // Thêm thông tin chi tiết chuyến đi
            TripDetailResponse tripDetail = tripRepository.findTripDetailById(tripId);
            List<TripStopResponse> tripStops = tripRepository.findTripStopsById(tripId);
            List<BusImageResponse> busImages = tripRepository.findBusImagesByBusId(tripDetail.getBusId());
            response.putAll(TripMapper.toTripDetail(tripDetail, tripStops, busImages));

            return response;
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi hệ thống khi cập nhật trạng thái: " + e.getMessage());
            return errorResponse;
        }
    }

    private void validateStatusTransition(TripStatus currentStatus, TripStatus newStatus) {
        // Logic kiểm tra tính hợp lệ của việc chuyển đổi trạng thái

        // Không thể thay đổi trạng thái của chuyến đã hoàn thành hoặc đã hủy
        if (currentStatus == TripStatus.arrived || currentStatus == TripStatus.cancelled) {
            throw new IllegalStateException("Không thể thay đổi trạng thái của chuyến đi đã " +
                    (currentStatus == TripStatus.arrived ? "hoàn thành" : "hủy"));
        }

        // Chỉ cho phép chuyển đổi theo logic nghiệp vụ
        switch (currentStatus) {
            case scheduled:
                // Từ scheduled có thể chuyển sang departed hoặc delayed
                if (newStatus != TripStatus.departed && newStatus != TripStatus.delayed) {
                    throw new IllegalStateException(
                            "Từ trạng thái scheduled chỉ có thể chuyển sang departed hoặc delayed");
                }
                break;
            case on_sell:
                // Giữ nguyên logic cũ cho on_sell
                if (newStatus != TripStatus.departed && newStatus != TripStatus.delayed &&
                        newStatus != TripStatus.cancelled) {
                    throw new IllegalStateException(
                            "Từ trạng thái on_sell chỉ có thể chuyển sang departed, delayed hoặc cancelled");
                }
                break;
            case departed:
                // Từ departed chỉ có thể chuyển sang delayed, arrived hoặc cancelled
                if (newStatus != TripStatus.delayed && newStatus != TripStatus.arrived &&
                        newStatus != TripStatus.cancelled) {
                    throw new IllegalStateException(
                            "Từ trạng thái departed chỉ có thể chuyển sang delayed, arrived hoặc cancelled");
                }
                break;
            case delayed:
                // Từ delayed chỉ có thể chuyển sang arrived hoặc cancelled
                if (newStatus != TripStatus.arrived && newStatus != TripStatus.cancelled) {
                    throw new IllegalStateException(
                            "Từ trạng thái delayed chỉ có thể chuyển sang arrived hoặc cancelled");
                }
                break;
            case arrived:
            case cancelled:
                // Đã được xử lý ở trên - không thể chuyển sang trạng thái khác
                break;
        }
    }

    @Override
    public List<TripByDriverResponseDTO> getTripsByDriverId(Long driverId) {
        List<Object[]> results = tripRepository.findTripsByDriverId(driverId);
        List<TripByDriverResponseDTO> trips = new ArrayList<>();

        for (Object[] result : results) {
            // Convert Timestamp to Instant safely
            Instant departureTime = null;
            Instant estimatedArrivalTime = null;

            if (result[1] != null) {
                if (result[1] instanceof Timestamp) {
                    departureTime = ((Timestamp) result[1]).toInstant();
                } else if (result[1] instanceof Instant) {
                    departureTime = (Instant) result[1];
                }
            }

            if (result[2] != null) {
                if (result[2] instanceof Timestamp) {
                    estimatedArrivalTime = ((Timestamp) result[2]).toInstant();
                } else if (result[2] instanceof Instant) {
                    estimatedArrivalTime = (Instant) result[2];
                }
            }

            TripByDriverResponseDTO trip = TripByDriverResponseDTO.builder()
                    .tripId(((Number) result[0]).longValue())
                    .departureTime(departureTime)
                    .estimatedArrivalTime(estimatedArrivalTime)
                    .status((String) result[3])
                    .pricePerSeat((BigDecimal) result[4])
                    .operatorName((String) result[5])
                    .routeId(((Number) result[6]).longValue())
                    .startCity((String) result[7])
                    .startAddress((String) result[8])
                    .endCity((String) result[9])
                    .endAddress((String) result[10])
                    .busLicensePlate((String) result[11])
                    .busModel((String) result[12])
                    .availableSeats(((Number) result[13]).intValue())
                    .totalSeats(((Number) result[14]).intValue())
                    .averageRating(result[15] != null ? ((Number) result[15]).doubleValue() : 0.0)
                    .build();
            trips.add(trip);
        }

        return trips;
    }

    @Override
    public List<TripFilterResponseDTO> getUpcomingTripsForDriver(Long driverId) {
        Instant currentTime = Instant.now();

        return tripRepository.findUpcomingTripsByDriverId(driverId, currentTime)
                .stream()
                .filter(trip -> trip.getStatus() != TripStatus.cancelled)
                .map(trip -> TripMapper.toDTO(trip, getAverageRating(trip.getId()), bookingRepository))
                .collect(Collectors.toList());
    }

    @Override
    public List<TopTripRevenueDTO> getTop10TripsByRevenueAndYear(Integer year) {
        {
            LocalDate now = LocalDate.now();
            int reportYear = (year != null) ? year : now.getYear();
            return tripRepository.findTop10TripsByRevenueAndYear(reportYear);
        }
    }

    /**
     * Helper method to get current user for audit logging
     */
    private User getCurrentUser() {
        try {
            String currentUserEmail = jwtUtils.getCurrentUserLogin().orElse(null);
            if (currentUserEmail != null) {
                return userRepository.findByEmail(currentUserEmail).orElse(null);
            }
            return null;
        } catch (Exception e) {
            // Return null if unable to get current user (e.g., system operations)
            return null;
        }
    }

    @Override
    public TripResponseByRegionDTO getTripsEachRegion() {
        final List<TripRouteResponse> northTrips = tripRepository.findTripsByRegion(LocationRegion.NORTH);
        final List<TripRouteResponse> centralTrips = tripRepository.findTripsByRegion(LocationRegion.CENTRAL);
        final List<TripRouteResponse> southTrips = tripRepository.findTripsByRegion(LocationRegion.SOUTH);
        TripResponseByRegionDTO response = new TripResponseByRegionDTO();
        response.setTripsByRegion(Map.of(
                LocationRegion.NORTH, northTrips,
                LocationRegion.CENTRAL, centralTrips,
                LocationRegion.SOUTH, southTrips));
        return response;
    }

    @Override
    public NextTripSeatsStatusResponseDTO getNextTripSeatsStatus(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi với ID: " + tripId));
        final Optional<SeatLayout> seatLayout = seatLayoutRepository.findSeatLayoutByTripId(tripId);
        if (seatLayout.isEmpty()) {
            throw new IllegalArgumentException("Chưa có sơ đồ ghế cho chuyến đi với ID: " + tripId);
        }
        NextTripSeatStatusDTO response = tripRepository.getNextTripSeatStatus(tripId);

        JsonNode layout = objectMapper.convertValue(seatLayout.get().getLayoutData(), JsonNode.class);
        int rows = layout.get("rows").asInt();
        int columns = layout.get("cols").asInt();
        int floors = layout.has("floors") ? layout.get("floors").asInt() : 1;
        final BusLayoutResponseDTO busLayout = new BusLayoutResponseDTO(rows, columns, floors);

        final List<TicketSeatStatusReponse> seatStatuses = ticketService.getTicketSeatStatusByTripId(tripId);

        NextTripSeatsStatusResponseDTO responseDTO = new NextTripSeatsStatusResponseDTO();
        responseDTO.setBusSeatsCount(response.getBusSeatsCount());
        responseDTO.setCheckedSeatsCount(response.getCheckedSeatsCount());
        responseDTO.setBookedSeatsCount(response.getBookedSeatsCount());
        responseDTO.setBusLayout(busLayout);
        responseDTO.setTripId(tripId);
        responseDTO.setSeatStatuses(seatStatuses);
        return responseDTO;
    }

    @Override
    public RoundTripFilterResponseDTO filterRoundTrips(RoundTripFilterRequestDTO filter, int page, int size) {
        Logger logger = Logger.getLogger(TripServiceImpl.class.getName());
        logger.info("Round trip filter: " + filter.toString());

        // Chuyển đổi RoundTripFilterRequestDTO sang TripFilterRequestDTO cho chiều đi
        TripFilterRequestDTO outboundFilter = convertToTripFilterRequest(filter);
        
        // Lọc chuyến đi (chiều đi)
        FilterResponseDTO outboundTrips = filterTrips(outboundFilter, page, size);

        // Nếu không phải khứ hồi, trả về kết quả chiều đi
        if (filter.getIsRoundTrip() == null || !filter.getIsRoundTrip()) {
            return RoundTripFilterResponseDTO.builder()
                    .outboundTrips(outboundTrips)
                    .returnTrips(null)
                    .isRoundTrip(false)
                    .returnTripMessage(null)
                    .build();
        }

        // Kiểm tra ngày về phải hợp lệ
        if (filter.getReturnDate() == null) {
            return RoundTripFilterResponseDTO.builder()
                    .outboundTrips(outboundTrips)
                    .returnTrips(null)
                    .isRoundTrip(true)
                    .returnTripMessage("Vui lòng chọn ngày về để tìm kiếm chuyến khứ hồi")
                    .build();
        }

        // Kiểm tra ngày về phải sau ngày đi
        if (filter.getDepartureDate() != null && filter.getReturnDate().isBefore(filter.getDepartureDate())) {
            return RoundTripFilterResponseDTO.builder()
                    .outboundTrips(outboundTrips)
                    .returnTrips(null)
                    .isRoundTrip(true)
                    .returnTripMessage("Ngày về phải sau ngày đi")
                    .build();
        }

        // Tạo filter cho chiều về (đảo ngược điểm đi/điểm đến)
        TripFilterRequestDTO returnFilter = convertToTripFilterRequest(filter);
        returnFilter.setStartLocation(filter.getEndLocation()); // Đảo ngược: điểm đến -> điểm đi
        returnFilter.setEndLocation(filter.getStartLocation()); // Đảo ngược: điểm đi -> điểm đến
        returnFilter.setDepartureDate(filter.getReturnDate());  // Ngày về

        // Lọc chuyến về (chiều về)
        FilterResponseDTO returnTrips = filterTrips(returnFilter, page, size);

        // Kiểm tra có chuyến về không
        String returnMessage = null;
        if (returnTrips.getData() == null || returnTrips.getData().isEmpty()) {
            returnMessage = "Không tìm thấy chuyến khứ hồi phù hợp cho ngày về đã chọn";
        }

        return RoundTripFilterResponseDTO.builder()
                .outboundTrips(outboundTrips)
                .returnTrips(returnTrips)
                .isRoundTrip(true)
                .returnTripMessage(returnMessage)
                .build();
    }

    /**
     * Chuyển đổi RoundTripFilterRequestDTO sang TripFilterRequestDTO
     */
    private TripFilterRequestDTO convertToTripFilterRequest(RoundTripFilterRequestDTO roundTripFilter) {
        TripFilterRequestDTO filter = new TripFilterRequestDTO();
        filter.setStartLocation(roundTripFilter.getStartLocation());
        filter.setEndLocation(roundTripFilter.getEndLocation());
        filter.setDepartureDate(roundTripFilter.getDepartureDate());
        filter.setBusModels(roundTripFilter.getBusModels());
        filter.setUntilTime(roundTripFilter.getUntilTime());
        filter.setTimeZone(roundTripFilter.getTimeZone());
        filter.setOperatorName(roundTripFilter.getOperatorName());
        filter.setAmenities(roundTripFilter.getAmenities());
        filter.setAvailableSeats(roundTripFilter.getAvailableSeats());
        filter.setSortBy(roundTripFilter.getSortBy());
        filter.setSortDirection(roundTripFilter.getSortDirection());
        filter.setSortBySecondary(roundTripFilter.getSortBySecondary());
        filter.setSortDirectionSecondary(roundTripFilter.getSortDirectionSecondary());
        return filter;
    }
}
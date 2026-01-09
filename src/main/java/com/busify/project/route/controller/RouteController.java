package com.busify.project.route.controller;

import com.busify.project.route.dto.response.PopularRouteResponse;
import com.busify.project.route.dto.response.RouteResponse;
import com.busify.project.route.dto.response.TopRouteRevenueDTO;
import com.busify.project.route.service.RouteService;
import com.busify.project.trip.dto.response.TripStopResponse;
import com.busify.project.trip.service.impl.TripServiceImpl;
import com.busify.project.common.dto.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Route", description = "Route API")
public class RouteController {
    private final RouteService routeService;
    private final TripServiceImpl tripService;

    @GetMapping("/popular-routes")
    @Operation(summary = "Get popular routes")
    public ApiResponse<List<PopularRouteResponse>> getPopularRoutes() {
        List<PopularRouteResponse> routes = routeService.getPopularRoutes();
        return ApiResponse.success("Popular routes fetched successfully", routes);
    }

    @GetMapping()
    @Operation(summary = "Get all routes")
    public ApiResponse<List<RouteResponse>> getAllRoutes() {
        List<RouteResponse> routes = routeService.getAllRoutes();
        return ApiResponse.success("All routes fetched successfully", routes);
    }

    // Admin endpoint: Top 10 routes có doanh thu cao nhất
    @GetMapping("/admin/top-revenue-routes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get top 10 routes by revenue (Admin only)")
    public ApiResponse<List<TopRouteRevenueDTO>> getTop10RoutesByRevenue(
            @RequestParam(value = "year", required = false) Integer year) {

        List<TopRouteRevenueDTO> topRoutes;
        topRoutes = routeService.getTop10RoutesByRevenueAndYear(year);

        return ApiResponse.<List<TopRouteRevenueDTO>>builder()
                .code(HttpStatus.OK.value())
                .message("Top 10 routes by revenue retrieved successfully")
                .result(topRoutes)
                .build();
    }

    // Admin endpoint: Top 10 trips có doanh thu cao nhất
    
    /**
     * Endpoint for frontend compatibility
     * GET /api/routes/trip/{tripId}/stop-locations
     * Returns stop locations for a specific trip
     */
    @GetMapping("/trip/{tripId}/stop-locations")
    @Operation(summary = "Get stop locations for a trip", 
               description = "Returns all stop locations for the specified trip ID")
    public ApiResponse<List<TripStopResponse>> getTripStopLocations(@PathVariable Long tripId) {
        try {
            List<TripStopResponse> tripStops = tripService.getTripStopsById(tripId);
            return ApiResponse.success("Lấy thông tin các điểm dừng của chuyến đi thành công", tripStops);
        } catch (Exception e) {
            return ApiResponse.internalServerError("Đã xảy ra lỗi khi lấy thông tin các điểm dừng: " + e.getMessage());
        }
    }

}
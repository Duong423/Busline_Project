package com.busify.project.payment.strategy.impl;

import com.busify.project.common.event.PaymentSuccessEvent;
import com.busify.project.common.publisher.BusifyEventPublisher;
import com.busify.project.payment.config.ZaloPayConfig;
import com.busify.project.payment.dto.request.PaymentRequestDTO;
import com.busify.project.payment.dto.response.PaymentResponseDTO;
import com.busify.project.payment.entity.Payment;
import com.busify.project.payment.enums.PaymentMethod;
import com.busify.project.payment.enums.PaymentStatus;
import com.busify.project.payment.exception.PaymentNotFoundException;
import com.busify.project.payment.repository.PaymentRepository;
import com.busify.project.payment.strategy.PaymentStrategy;
import com.busify.project.payment.util.ZaloPayUtil;
import com.busify.project.trip_seat.services.SeatReleaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ZaloPayPaymentStrategy implements PaymentStrategy {

    private final ZaloPayConfig zaloPayConfig;
    private final PaymentRepository paymentRepository;
    private final BusifyEventPublisher eventPublisher;
    private final SeatReleaseService seatReleaseService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String createPaymentUrl(Payment paymentEntity, PaymentRequestDTO paymentRequest) {
        try {
            // Generate app_trans_id
            String appTransId = ZaloPayUtil.generateAppTransId();
            
            // Prepare order data
            String appUser = paymentEntity.getBooking().getCustomer() != null 
                ? paymentEntity.getBooking().getCustomer().getEmail() 
                : paymentEntity.getBooking().getGuestEmail();
            
            long amount = paymentEntity.getAmount().longValue();
            String description = "Thanh toan ve xe buyt cho booking " + paymentEntity.getBooking().getId();
            
            // Create embed_data - MINIMAL for localhost testing
            JSONObject embedData = new JSONObject();
            embedData.put("redirecturl", "http://localhost:8080/api/payments/zalopay/return?apptransid=" + appTransId);
            String embedDataJson = embedData.toString();
            
            // Create item (JSON array)
            String item = "[]"; // Empty array for simple use case
            
            // Generate app_time ONCE and reuse it
            long appTime = ZaloPayUtil.getCurrentTimestamp();
            
            // Create order
            Map<String, Object> order = new HashMap<>();
            order.put("app_id", Integer.parseInt(zaloPayConfig.getAppId()));
            order.put("app_user", appUser);
            order.put("app_time", appTime);
            order.put("amount", amount);
            order.put("app_trans_id", appTransId);
            order.put("embed_data", embedDataJson);
            order.put("item", item);
            order.put("description", description);
            order.put("bank_code", ""); // Empty for all payment methods
            
            // Generate MAC signature
            // Format: app_id|app_trans_id|app_user|amount|app_time|embed_data|item
            String data = zaloPayConfig.getAppId() + "|" 
                + appTransId + "|" 
                + appUser + "|" 
                + amount + "|" 
                + appTime + "|" 
                + embedDataJson + "|" 
                + item;
            
            log.info("ZaloPay MAC data: {}", data);
            
            String mac = ZaloPayUtil.generateHMAC(zaloPayConfig.getKey1(), data);
            order.put("mac", mac);
            
            log.info("ZaloPay request order: {}", new JSONObject(order).toString());
            
            // Call ZaloPay API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(order, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                zaloPayConfig.getEndpoint(), 
                request, 
                String.class
            );
            
            log.info("ZaloPay response: {}", response.getBody());
            
            // Parse response
            JSONObject jsonResponse = new JSONObject(response.getBody());
            int returnCode = jsonResponse.getInt("return_code");
            
            if (returnCode == 1) {
                String orderUrl = jsonResponse.getString("order_url");
                String zpTransToken = jsonResponse.getString("zp_trans_token");
                String qrCode = jsonResponse.optString("qr_code", "");
                
                // Save app_trans_id to payment entity
                paymentEntity.setPaymentGatewayId(appTransId);
                paymentRepository.save(paymentEntity);
                
                log.info("Created ZaloPay payment with QR for payment ID: {}, app_trans_id: {}", 
                    paymentEntity.getPaymentId(), appTransId);
                
                // IMPORTANT: ZaloPay Sandbox no longer supports web browser redirect
                // Must use QR Code or deep link to open in ZaloPay app
                // Return format: qrcode:{qr_data}|deeplink:{url}
                return "qrcode:" + qrCode + "|deeplink:" + orderUrl;
            } else {
                String returnMessage = jsonResponse.optString("return_message", "Unknown error");
                log.error("ZaloPay create order failed - return_code: {}, message: {}", returnCode, returnMessage);
                throw new RuntimeException("ZaloPay create order failed: " + returnMessage);
            }
            
        } catch (Exception e) {
            log.error("Error creating ZaloPay payment URL: ", e);
            throw new RuntimeException("Failed to create ZaloPay payment URL", e);
        }
    }

    @Override
    public PaymentResponseDTO executePayment(Payment paymentEntity, String paymentId, String payerId) {
        try {
            // ZaloPay sử dụng callback để thông báo kết quả thanh toán
            // Method này có thể được gọi từ callback handler
            paymentEntity.setStatus(PaymentStatus.completed);
            paymentEntity.setPaidAt(Instant.now());

            paymentRepository.save(paymentEntity);

            log.info("ZaloPay payment executed successfully for payment ID: {}", paymentEntity.getPaymentId());

            // Publish payment success event
            eventPublisher.publishEvent(new PaymentSuccessEvent(
                this,
                "ZaloPay payment successful for transaction: " + paymentEntity.getPaymentGatewayId(),
                paymentEntity
            ));

            return PaymentResponseDTO.builder()
                    .paymentId(paymentEntity.getPaymentId())
                    .status(PaymentStatus.completed)
                    .bookingId(paymentEntity.getBooking().getId())
                    .build();

        } catch (Exception e) {
            log.error("Error executing ZaloPay payment: ", e);
            paymentEntity.setStatus(PaymentStatus.failed);
            paymentRepository.save(paymentEntity);

            throw new RuntimeException("ZaloPay payment execution failed", e);
        }
    }

    @Override
    public PaymentResponseDTO cancelPayment(Payment paymentEntity, String paymentId) {
        try {
            // ZaloPay không hỗ trợ cancel trực tiếp từ merchant
            // Chỉ cập nhật trạng thái trong hệ thống
            paymentEntity.setStatus(PaymentStatus.cancelled);
            paymentRepository.save(paymentEntity);

            log.info("ZaloPay payment cancelled for payment ID: {}", paymentEntity.getPaymentId());

            return PaymentResponseDTO.builder()
                    .paymentId(paymentEntity.getPaymentId())
                    .status(PaymentStatus.cancelled)
                    .bookingId(paymentEntity.getBooking().getId())
                    .build();

        } catch (Exception e) {
            log.error("Error cancelling ZaloPay payment: ", e);
            throw new RuntimeException("Failed to cancel ZaloPay payment", e);
        }
    }

    @Override
    public boolean supports(String paymentMethod) {
        return PaymentMethod.ZALOPAY.name().equalsIgnoreCase(paymentMethod);
    }

    /**
     * Handle callback from ZaloPay
     * This method should be called from the callback endpoint
     */
    public PaymentResponseDTO handleCallback(Map<String, String> callbackData) {
        try {
            // Extract callback data
            String appId = callbackData.get("appid");
            String appTransId = callbackData.get("apptransid");
            String pmcId = callbackData.get("pmcid");
            String bankCode = callbackData.get("bankcode");
            String amount = callbackData.get("amount");
            String discountAmount = callbackData.get("discountamount");
            String status = callbackData.get("status");
            String checksum = callbackData.get("checksum");
            
            // Verify checksum
            String checksumData = appId + "|" + appTransId + "|" + pmcId + "|" + bankCode + "|" 
                + amount + "|" + discountAmount + "|" + status;
            
            boolean isValid = ZaloPayUtil.verifyCallback(zaloPayConfig.getKey2(), checksumData, checksum);
            
            if (!isValid) {
                throw new RuntimeException("Invalid ZaloPay callback checksum");
            }
            
            // Find payment by app_trans_id
            Payment payment = paymentRepository.findByPaymentGatewayId(appTransId)
                .orElseThrow(() -> PaymentNotFoundException.notFound());
            
            // Lấy danh sách booking IDs (hỗ trợ round-trip)
            List<Long> allBookingIds = payment.getBookingIdList();
            
            // Update payment status based on callback status
            if ("1".equals(status)) {
                // Payment successful
                payment.setStatus(PaymentStatus.completed);
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
                
                // Cancel seat release job cho TẤT CẢ booking
                if (allBookingIds != null && !allBookingIds.isEmpty()) {
                    for (Long bookingId : allBookingIds) {
                        seatReleaseService.cancelReleaseTask(bookingId);
                        log.info("Cancelled seat release for booking: {}", bookingId);
                    }
                } else if (payment.getBooking() != null) {
                    seatReleaseService.cancelReleaseTask(payment.getBooking().getId());
                }
                
                // Publish payment success event
                eventPublisher.publishEvent(new PaymentSuccessEvent(
                    this,
                    "ZaloPay callback payment successful for transaction: " + appTransId,
                    payment
                ));
                
                log.info("ZaloPay payment callback processed successfully for payment ID: {}", 
                    payment.getPaymentId());
                
                return PaymentResponseDTO.builder()
                    .paymentId(payment.getPaymentId())
                    .status(PaymentStatus.completed)
                    .bookingId(payment.getBooking() != null ? payment.getBooking().getId() : null)
                    .bookingIds(allBookingIds)
                    .build();
            } else {
                // Payment failed
                payment.setStatus(PaymentStatus.failed);
                paymentRepository.save(payment);
                
                log.warn("ZaloPay payment failed for payment ID: {}", payment.getPaymentId());
                
                return PaymentResponseDTO.builder()
                    .paymentId(payment.getPaymentId())
                    .status(PaymentStatus.failed)
                    .bookingId(payment.getBooking() != null ? payment.getBooking().getId() : null)
                    .bookingIds(allBookingIds)
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error handling ZaloPay callback: ", e);
            throw new RuntimeException("Failed to handle ZaloPay callback", e);
        }
    }

    /**
     * Query ZaloPay payment status and process if successful
     * Used when callback is not received (localhost development)
     * Returns PaymentResponseDTO for controller to use
     */
    public PaymentResponseDTO queryAndProcessPayment(String appTransId) {
        try {
            log.debug("Querying ZaloPay payment status for app_trans_id: {}", appTransId);
            
            // Prepare query request
            Map<String, Object> queryOrder = new HashMap<>();
            queryOrder.put("app_id", Integer.parseInt(zaloPayConfig.getAppId()));
            queryOrder.put("app_trans_id", appTransId);
            
            // Generate MAC for query
            String queryData = zaloPayConfig.getAppId() + "|" + appTransId + "|" + zaloPayConfig.getKey1();
            String mac = ZaloPayUtil.generateHMAC(zaloPayConfig.getKey1(), queryData);
            queryOrder.put("mac", mac);
            
            // Call ZaloPay query API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(queryOrder, headers);
            
            String queryEndpoint = "https://sb-openapi.zalopay.vn/v2/query";
            ResponseEntity<String> response = restTemplate.postForEntity(queryEndpoint, request, String.class);
            
            log.debug("ZaloPay query response: {}", response.getBody());
            
            // Parse response
            JSONObject jsonResponse = new JSONObject(response.getBody());
            int returnCode = jsonResponse.getInt("return_code");
            
            if (returnCode == 1) {
                // Payment successful - process and return response
                PaymentResponseDTO result = processPaymentSuccess(appTransId);
                log.debug("Successfully processed ZaloPay payment via query for: {}", appTransId);
                return result;
            } else {
                log.debug("ZaloPay payment not yet completed for: {}, return_code: {}", appTransId, returnCode);
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error querying ZaloPay payment status: ", e);
            throw new RuntimeException("Failed to query ZaloPay payment", e);
        }
    }

    /**
     * Process payment success - update status, publish event, return response
     * Returns PaymentResponseDTO with bookingId and bookingIds for ticket creation
     */
    private PaymentResponseDTO processPaymentSuccess(String appTransId) {
        try {
            // Find payment by app_trans_id
            Payment payment = paymentRepository.findByPaymentGatewayId(appTransId)
                .orElseThrow(() -> PaymentNotFoundException.notFound());
            
            // Payment successful
            payment.setStatus(PaymentStatus.completed);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);
            
            // Cancel seat release job cho TẤT CẢ booking (hỗ trợ round-trip)
            List<Long> allBookingIds = payment.getBookingIdList();
            if (allBookingIds != null && !allBookingIds.isEmpty()) {
                for (Long bookingId : allBookingIds) {
                    seatReleaseService.cancelReleaseTask(bookingId);
                    log.info("Cancelled seat release for booking: {}", bookingId);
                }
            } else if (payment.getBooking() != null) {
                seatReleaseService.cancelReleaseTask(payment.getBooking().getId());
            }
            
            // Publish payment success event
            eventPublisher.publishEvent(new PaymentSuccessEvent(
                this,
                "ZaloPay payment successful for transaction: " + appTransId,
                payment
            ));
            
            log.info("ZaloPay payment processed successfully for payment ID: {}", payment.getPaymentId());
            
            // Return response with booking ID và bookingIds cho round-trip
            return PaymentResponseDTO.builder()
                .paymentId(payment.getPaymentId())
                .status(PaymentStatus.completed)
                .bookingId(payment.getBooking() != null ? payment.getBooking().getId() : null)
                .bookingIds(allBookingIds)
                .build();
            
        } catch (Exception e) {
            log.error("Error processing payment success: ", e);
            throw new RuntimeException("Failed to process payment success", e);
        }
    }
}

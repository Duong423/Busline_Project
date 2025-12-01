package com.busify.project.payment.controller;

import com.busify.project.common.dto.response.ApiResponse;
import com.busify.project.payment.dto.request.PaymentRequestDTO;
import com.busify.project.payment.dto.response.PaymentDetailResponseDTO;
import com.busify.project.payment.dto.response.PaymentResponseDTO;
import com.busify.project.payment.entity.Payment;
import com.busify.project.payment.enums.PaymentStatus;
import com.busify.project.payment.service.impl.PaymentServiceImpl;
import com.busify.project.payment.strategy.impl.VNPayPaymentStrategy;
import com.busify.project.payment.strategy.impl.ZaloPayPaymentStrategy;
import com.busify.project.ticket.service.TicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "Payment API")
public class PaymentController {

    private final PaymentServiceImpl paymentService;
    private final VNPayPaymentStrategy vnPayPaymentStrategy;
    private final ZaloPayPaymentStrategy zaloPayPaymentStrategy;
    private final TicketService ticketService;

    @PostMapping("/create")
    @Operation(summary = "Create a new payment")
    public ApiResponse<PaymentResponseDTO> createPayment(@RequestBody PaymentRequestDTO paymentRequest) {
        try {
            PaymentResponseDTO response = paymentService.createPayment(paymentRequest);
            return ApiResponse.<PaymentResponseDTO>builder()
                    .code(HttpStatus.OK.value())
                    .message("Payment created successfully")
                    .result(response)
                    .build();
        } catch (Exception e) {
            log.error("Error creating payment: ", e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Create payment fail. Error " + e.getMessage());
        }
    }

    @GetMapping("/status/{paymentId}")
    @Operation(summary = "Get payment status by payment ID")
    public ApiResponse<PaymentResponseDTO> getPaymentStatus(@PathVariable Long paymentId) {
        try {
            log.info("Getting payment status for payment ID: {}", paymentId);

            return ApiResponse.<PaymentResponseDTO>builder()
                    .code(HttpStatus.OK.value())
                    .message("Payment status retrieved successfully")
                    .result(PaymentResponseDTO.builder()
                            .paymentId(paymentId)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Error getting payment status: ", e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error getting payment status");
        }
    }

    @GetMapping("/success")
    @Operation(summary = "Handle successful payment callback")
    public ApiResponse<PaymentResponseDTO> paymentSuccess(@RequestParam("paymentId") String paypalPaymentId,
            @RequestParam("PayerID") String payerId) {
        try {
            log.info("PayPal success callback - PayPal Payment ID: {}, Payer ID: {}",
                    paypalPaymentId, payerId);

            PaymentResponseDTO response = paymentService.executePaymentByPayPalId(paypalPaymentId, payerId);
            if (response.getStatus().name().equals("completed")) {
                // Lấy bookingId từ response hoặc từ Payment entity
                Long bookingId = response.getBookingId();
                System.out.println("Booking Id: " + bookingId);
                ticketService.createTicketsFromBooking(bookingId, null);
                return ApiResponse.<PaymentResponseDTO>builder()
                        .code(HttpStatus.OK.value())
                        .message("Payment executed successfully")
                        .result(response)
                        .build();
            } else {
                return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Payment not completed");
            }
        } catch (Exception e) {
            log.error("Error executing PayPal payment: ", e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error executing PayPal payment");
        }
    }

    @GetMapping("/cancel")
    @Operation(summary = "Handle cancelled payment callback")
    public ApiResponse<PaymentResponseDTO> paymentCancel(
            @RequestParam(value = "paymentId", required = false) String paypalPaymentId) {
        try {
            if (paypalPaymentId != null) {
                log.info("PayPal cancel callback - PayPal Payment ID: {}", paypalPaymentId);
                // Tìm payment trong DB và cancel
            }
            // Redirect to cancel page

            PaymentResponseDTO response = paymentService.cancelPaymentByPayPalId(paypalPaymentId);
            return ApiResponse.<PaymentResponseDTO>builder()
                    .code(HttpStatus.OK.value())
                    .message("Payment cancelled successfully")
                    .result(response)
                    .build();
        } catch (Exception e) {
            log.error("Error cancelling PayPal payment: ", e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error cancelling PayPal payment");
        }
    }

    @GetMapping("/debug")
    @Operation(summary = "Debug payment callback")
    public RedirectView debugCallback(@RequestParam Map<String, String> allParams) {
        log.info("PayPal Debug Callback - All parameters: {}", allParams);
        return new RedirectView("http://localhost:8080/paypal-debug.html?" +
                allParams.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "&" + b)
                        .orElse(""));
    }

    // VNPay callback endpoints
    @GetMapping("/vnpay/callback")
    @Operation(summary = "Handle VNPay payment callback")
    public ApiResponse<PaymentResponseDTO> vnPayCallback(@RequestParam Map<String, String> allParams) {
        try {
            log.info("VNPay callback received with parameters: {}", allParams);

            String transactionCode = allParams.get("vnp_TxnRef");
            String responseCode = allParams.get("vnp_ResponseCode");
            String amount = allParams.get("vnp_Amount");
            String orderInfo = allParams.get("vnp_OrderInfo");
            String vnpTransactionNo = allParams.get("vnp_TransactionNo"); // VNPay internal transaction ID

            // Xác thực chữ ký (tùy chọn - có thể bỏ qua để đơn giản)
            // boolean isValid = VNPayUtil.verifyCallback(allParams,
            // vnPayConfig.getSecretKey());
            // if (!isValid) {
            // throw new RuntimeException("Invalid VNPay signature");
            // }

            PaymentResponseDTO response = vnPayPaymentStrategy.handleCallback(
                    transactionCode, responseCode, amount, orderInfo, vnpTransactionNo);

            // Lấy bookingId từ response hoặc từ Payment entity
            Long bookingId = response.getBookingId();
            System.out.println("Booking Id: " + bookingId);
            ticketService.createTicketsFromBooking(bookingId, null);

            return ApiResponse.<PaymentResponseDTO>builder()
                    .code(HttpStatus.OK.value())
                    .message("VNPay payment processed successfully")
                    .result(response)
                    .build();

        } catch (Exception e) {
            log.error("Error handling VNPay callback: ", e);
            return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Error handling VNPay callback");
        }
    }

    // ZaloPay callback endpoint
    @PostMapping("/zalopay/callback")
    @Operation(summary = "Handle ZaloPay payment callback")
    public ApiResponse<Map<String, Object>> zaloPayCallback(@RequestBody Map<String, String> callbackData) {
        try {
            log.info("ZaloPay callback received: {}", callbackData);

            // Handle callback through strategy
            PaymentResponseDTO response = zaloPayPaymentStrategy.handleCallback(callbackData);

            // Create tickets if payment successful
            if (response.getStatus().name().equals("completed")) {
                Long bookingId = response.getBookingId();
                ticketService.createTicketsFromBooking(bookingId, null);
            }

            // Return response to ZaloPay (must return specific format)
            Map<String, Object> result = new HashMap<>();
            result.put("return_code", 1); // 1 = success, other = error
            result.put("return_message", "success");

            return ApiResponse.<Map<String, Object>>builder()
                    .code(HttpStatus.OK.value())
                    .message("ZaloPay callback processed successfully")
                    .result(result)
                    .build();

        } catch (Exception e) {
            log.error("Error handling ZaloPay callback: ", e);
            
            // Return error response to ZaloPay
            Map<String, Object> result = new HashMap<>();
            result.put("return_code", 0);
            result.put("return_message", "error: " + e.getMessage());
            
            return ApiResponse.<Map<String, Object>>builder()
                    .code(HttpStatus.BAD_REQUEST.value())
                    .message("Error handling ZaloPay callback")
                    .result(result)
                    .build();
        }
    }

    // ZaloPay return endpoint - user redirects here after payment
    @GetMapping("/zalopay/return")
    @Operation(summary = "Handle ZaloPay user return after payment")
    public RedirectView zaloPayReturn(@RequestParam(required = false) String apptransid) {
        try {
            log.info("ZaloPay return received with apptransid: {}", apptransid);
            
            if (apptransid != null) {
                // Clean duplicate parameter if exists (e.g., "251201_609643,251201_609643")
                String cleanAppTransId = apptransid.split(",")[0].trim();
                log.info("Cleaned app_trans_id: {}", cleanAppTransId);
                
                // Query ZaloPay to check payment status and process
                PaymentResponseDTO response = zaloPayPaymentStrategy.queryAndProcessPayment(cleanAppTransId);
                
                // If payment successful, create tickets (same as VNPAY flow)
                if (response != null && response.getStatus() == PaymentStatus.completed) {
                    Long bookingId = response.getBookingId();
                    log.info("Creating tickets for Booking ID: {}", bookingId);
                    ticketService.createTicketsFromBooking(bookingId, null);
                    
                    // Redirect to frontend success page
                    return new RedirectView("http://localhost:3000/bookingresult/" + response.getPaymentId());
                }
            }
            
            // Redirect to processing page if status unknown
            return new RedirectView("http://localhost:3000/payment/success?status=processing");
        } catch (Exception e) {
            log.error("Error handling ZaloPay return: ", e);
            return new RedirectView("http://localhost:3000/payment/failed");
        }
    }

    // Get payment by booking ID - for frontend to check status
    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment status by booking ID")
    public ApiResponse<PaymentResponseDTO> getPaymentByBookingId(@PathVariable Long bookingId) {
        try {
            Payment payment = paymentService.getPaymentByBookingId(bookingId);
            
            return ApiResponse.<PaymentResponseDTO>builder()
                    .code(HttpStatus.OK.value())
                    .message("Payment retrieved successfully")
                    .result(PaymentResponseDTO.builder()
                            .paymentId(payment.getPaymentId())
                            .status(payment.getStatus())
                            .bookingId(payment.getBooking().getId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Error getting payment by booking ID: ", e);
            return ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Payment not found");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment details by ID")
    public ApiResponse<PaymentDetailResponseDTO> getPaymentDetails(@PathVariable("id") Integer paymentId) {
        // Logic to retrieve payment details by paymentId
        PaymentDetailResponseDTO paymentDetails = paymentService.getPaymentDetails(paymentId.longValue());
        return ApiResponse.<PaymentDetailResponseDTO>builder()
                .code(HttpStatus.OK.value())
                .message("Payment details retrieved successfully")
                .result(paymentDetails)
                .build();
    }

}

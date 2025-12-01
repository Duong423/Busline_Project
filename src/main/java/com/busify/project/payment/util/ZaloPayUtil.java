package com.busify.project.payment.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class ZaloPayUtil {

    /**
     * Generate HMAC SHA256 signature for ZaloPay
     * @param key Secret key (key1 for create order, key2 for callback verification)
     * @param data Data string to sign
     * @return Hex string of HMAC SHA256
     */
    public static String generateHMAC(String key, String data) {
        try {
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKeySpec);
            
            byte[] hashBytes = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC SHA256", e);
        }
    }

    /**
     * Verify callback signature from ZaloPay
     * @param key Secret key2
     * @param data Data string to verify
     * @param receivedMac MAC received from ZaloPay
     * @return true if signature is valid
     */
    public static boolean verifyCallback(String key, String data, String receivedMac) {
        String calculatedMac = generateHMAC(key, data);
        return calculatedMac.equalsIgnoreCase(receivedMac);
    }

    /**
     * Generate app_trans_id format: yyMMdd_xxxxxx (current date + random 6 digits)
     * @return app_trans_id
     */
    public static String generateAppTransId() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMdd");
        String currentDate = sdf.format(new java.util.Date());
        
        // Generate random 6 digits
        int randomNumber = (int) (Math.random() * 1000000);
        String randomPart = String.format("%06d", randomNumber);
        
        return currentDate + "_" + randomPart;
    }

    /**
     * Get current timestamp in milliseconds
     * @return current timestamp
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}

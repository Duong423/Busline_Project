-- ===================================
-- Migration: Update payment_method column
-- Date: 2025-12-01
-- Purpose: Support ZALOPAY payment method
-- ===================================

USE busify;

-- Backup current data (optional)
-- CREATE TABLE payments_backup AS SELECT * FROM payments WHERE 1=0;

-- Check current column definition
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'busify' 
  AND TABLE_NAME = 'payments' 
  AND COLUMN_NAME = 'payment_method';

-- Update ENUM to add ZALOPAY payment method
-- Copy all existing values from screenshot and add ZALOPAY
ALTER TABLE payments 
MODIFY COLUMN payment_method ENUM(
    'BANK_TRANSFER',
    'CREDIT_CARD', 
    'PAYPAL',
    'PAY_LATER',
    'VNPAY',
    'ZALOPAY'
) NOT NULL
COMMENT 'Payment method: supports multiple payment gateways';

-- Verify the change
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'busify' 
  AND TABLE_NAME = 'payments' 
  AND COLUMN_NAME = 'payment_method';

-- Test query (should return no errors)
SELECT payment_method, COUNT(*) as count
FROM payments
GROUP BY payment_method;

-- ===================================
-- DONE! Now you can use ZALOPAY
-- ===================================

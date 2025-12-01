-- Fix payment_method column to support ZALOPAY
-- Run this SQL script in your MySQL database

-- Check current column definition
DESCRIBE busify.payments;

-- Update payment_method column to support longer values
-- Option 1: Change to VARCHAR with longer length
ALTER TABLE busify.payments 
MODIFY COLUMN payment_method VARCHAR(20) NOT NULL;

-- Option 2: Change to ENUM with all payment methods (recommended for data integrity)
-- ALTER TABLE busify.payments 
-- MODIFY COLUMN payment_method ENUM('PAYPAL', 'VNPAY', 'ZALOPAY', 'CREDIT_CARD', 'COD') NOT NULL;

-- Verify the change
DESCRIBE busify.payments;

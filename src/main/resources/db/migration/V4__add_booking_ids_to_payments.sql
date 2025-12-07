-- Migration: Add booking_ids column to payments table for round-trip support
-- This allows a single payment to be linked to multiple bookings (e.g., outbound + return trips)

-- Add new column booking_ids to store multiple booking IDs as comma-separated string
ALTER TABLE payments ADD COLUMN IF NOT EXISTS booking_ids VARCHAR(500) NULL;

-- Make booking_id nullable to support cases where only booking_ids is used
ALTER TABLE payments ALTER COLUMN booking_id DROP NOT NULL;

-- Add index for better query performance on booking_ids
CREATE INDEX IF NOT EXISTS idx_payments_booking_ids ON payments(booking_ids);

-- Add comment to explain the column usage
COMMENT ON COLUMN payments.booking_ids IS 'Comma-separated list of booking IDs for round-trip payments. Format: "id1,id2". NULL for single booking payments.';
COMMENT ON COLUMN payments.booking_id IS 'Primary booking ID (backward compatible). For round-trip, this contains the first booking ID.';

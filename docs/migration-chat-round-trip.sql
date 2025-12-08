-- Migration: Add round-trip support to conversation_contexts table
-- Date: 2025-12-08
-- Description: Add return_date and is_round_trip columns for round-trip booking support in chat AI

-- Add return_date column
ALTER TABLE conversation_contexts 
ADD COLUMN IF NOT EXISTS return_date DATE;

-- Add is_round_trip column
ALTER TABLE conversation_contexts 
ADD COLUMN IF NOT EXISTS is_round_trip BOOLEAN DEFAULT FALSE;

-- Add comment for documentation
COMMENT ON COLUMN conversation_contexts.return_date IS 'Ngày về cho vé khứ hồi';
COMMENT ON COLUMN conversation_contexts.is_round_trip IS 'Có phải đặt vé khứ hồi không';

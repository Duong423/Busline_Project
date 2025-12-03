-- Create conversation_contexts table for storing user chat context
CREATE TABLE IF NOT EXISTS conversation_contexts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    session_id VARCHAR(255),
    current_intent VARCHAR(50),
    departure VARCHAR(255),
    destination VARCHAR(255),
    departure_date DATE,
    number_of_tickets INT,
    bus_type VARCHAR(100),
    price_min DOUBLE,
    price_max DOUBLE,
    last_user_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_user_email (user_email),
    INDEX idx_session_id (session_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_user_updated (user_email, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

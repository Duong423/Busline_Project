-- Script để cleanup duplicate tickets trong database
-- Chạy script này để xóa các vé bị duplicate (cùng booking_id và seat_number)

-- Bước 1: Tìm các tickets bị duplicate
SELECT 
    t1.ticket_id,
    t1.booking_id,
    t1.seat_number,
    t1.price,
    b.booking_code,
    b.total_amount,
    COUNT(*) OVER (PARTITION BY t1.booking_id, t1.seat_number) as duplicate_count
FROM tickets t1
JOIN bookings b ON t1.booking_id = b.id
HAVING duplicate_count > 1
ORDER BY t1.booking_id, t1.seat_number;

-- Bước 2: Xem tổng số tickets bị duplicate
SELECT 
    COUNT(*) as total_duplicate_tickets,
    COUNT(DISTINCT booking_id) as affected_bookings
FROM (
    SELECT 
        t.ticket_id,
        t.booking_id,
        t.seat_number,
        COUNT(*) OVER (PARTITION BY t.booking_id, t.seat_number) as dup_count
    FROM tickets t
) sub
WHERE dup_count > 1;

-- Bước 3: Backup tickets trước khi xóa
CREATE TABLE IF NOT EXISTS tickets_backup_duplicate_cleanup AS
SELECT t.*, NOW() as backup_date
FROM tickets t
WHERE EXISTS (
    SELECT 1
    FROM tickets t2
    WHERE t2.booking_id = t.booking_id
      AND t2.seat_number = t.seat_number
      AND t2.ticket_id != t.ticket_id
);

-- Bước 4: Xóa các tickets duplicate, giữ lại ticket được tạo đầu tiên
-- (ticket có ticket_id nhỏ nhất)
DELETE t1
FROM tickets t1
INNER JOIN (
    SELECT 
        booking_id,
        seat_number,
        MIN(ticket_id) as min_ticket_id
    FROM tickets
    GROUP BY booking_id, seat_number
    HAVING COUNT(*) > 1
) t2 ON t1.booking_id = t2.booking_id 
    AND t1.seat_number = t2.seat_number
    AND t1.ticket_id > t2.min_ticket_id;

-- Bước 5: Verify kết quả - không còn duplicate
SELECT 
    booking_id,
    seat_number,
    COUNT(*) as count
FROM tickets
GROUP BY booking_id, seat_number
HAVING COUNT(*) > 1;
-- Kết quả phải empty (0 rows)

-- Bước 6: Kiểm tra số tickets bị xóa
SELECT 
    COUNT(*) as deleted_tickets,
    (SELECT COUNT(*) FROM tickets_backup_duplicate_cleanup) - COUNT(*) as tickets_removed
FROM tickets
WHERE ticket_id IN (SELECT ticket_id FROM tickets_backup_duplicate_cleanup);

-- Bước 7 (Optional): Thêm unique constraint để ngăn duplicate trong tương lai
-- ALTER TABLE tickets 
-- ADD CONSTRAINT unique_booking_seat 
-- UNIQUE (booking_id, seat_number);

-- Bước 8: Kiểm tra các bookings có vấn đề về giá
-- (Tổng giá các tickets khác với booking.total_amount)
SELECT 
    b.id as booking_id,
    b.booking_code,
    b.total_amount as booking_total,
    SUM(t.price) as tickets_total,
    COUNT(t.ticket_id) as ticket_count,
    b.total_amount - SUM(t.price) as price_difference
FROM bookings b
JOIN tickets t ON b.id = t.booking_id
GROUP BY b.id, b.booking_code, b.total_amount
HAVING ABS(b.total_amount - SUM(t.price)) > 0.01
ORDER BY price_difference DESC;

-- Bước 9: Fix giá cho các tickets nếu cần
-- (Chỉ chạy nếu bước 8 phát hiện có vấn đề)
-- UPDATE tickets t
-- JOIN bookings b ON t.booking_id = b.id
-- JOIN (
--     SELECT 
--         booking_id,
--         COUNT(*) as seat_count
--     FROM tickets
--     GROUP BY booking_id
-- ) tc ON b.id = tc.booking_id
-- SET t.price = b.total_amount / tc.seat_count
-- WHERE t.booking_id IN (
--     -- Chỉ update các bookings có vấn đề về giá
--     SELECT booking_id FROM ...
-- );

-- Bước 10: Log kết quả cleanup
INSERT INTO audit_logs (action, target_entity, details, created_at)
SELECT 
    'CLEANUP_DUPLICATE_TICKETS',
    'TICKETS',
    CONCAT('Cleaned up ', COUNT(*), ' duplicate tickets from ', 
           COUNT(DISTINCT booking_id), ' bookings'),
    NOW()
FROM tickets_backup_duplicate_cleanup;

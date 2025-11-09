-- ==========================================
-- KIỂM TRA CHUYẾN ĐÀ NẴNG - ĐÀ LẠT
-- ==========================================

-- 1. Kiểm tra tất cả locations có "Đà Nẵng"
SELECT 
    location_id,
    name,
    city,
    address
FROM location
WHERE LOWER(name) LIKE '%đà nẵng%' 
   OR LOWER(city) LIKE '%đà nẵng%'
   OR LOWER(name) LIKE '%da nang%'
   OR LOWER(city) LIKE '%da nang%';

-- 2. Kiểm tra tất cả locations có "Đà Lạt"
SELECT 
    location_id,
    name,
    city,
    address
FROM location
WHERE LOWER(name) LIKE '%đà lạt%' 
   OR LOWER(city) LIKE '%đà lạt%'
   OR LOWER(name) LIKE '%da lat%'
   OR LOWER(city) LIKE '%da lat%';

-- 3. Kiểm tra routes từ Đà Nẵng đến Đà Lạt (BẤT KỲ location_id nào)
SELECT 
    r.route_id,
    r.name AS route_name,
    sl.location_id AS start_loc_id,
    sl.name AS start_location,
    sl.city AS start_city,
    el.location_id AS end_loc_id,
    el.name AS end_location,
    el.city AS end_city,
    r.default_duration_minutes
FROM route r
JOIN location sl ON r.start_location_id = sl.location_id
JOIN location el ON r.end_location_id = el.location_id
WHERE (LOWER(sl.city) LIKE '%đà nẵng%' OR LOWER(sl.name) LIKE '%đà nẵng%')
  AND (LOWER(el.city) LIKE '%đà lạt%' OR LOWER(el.name) LIKE '%đà lạt%');

-- 4. Kiểm tra trips trên tuyến Đà Nẵng - Đà Lạt ngày 10/11/2025
SELECT 
    t.trip_id,
    t.departure_time,
    t.estimated_arrival_time,
    t.price_per_seat,
    t.available_seats,
    t.status,
    r.route_id,
    r.name AS route_name,
    sl.location_id AS start_loc_id,
    sl.name AS start_location,
    el.location_id AS end_loc_id,
    el.name AS end_location
FROM trip t
JOIN route r ON t.route_id = r.route_id
JOIN location sl ON r.start_location_id = sl.location_id
JOIN location el ON r.end_location_id = el.location_id
WHERE (LOWER(sl.city) LIKE '%đà nẵng%' OR LOWER(sl.name) LIKE '%đà nẵng%')
  AND (LOWER(el.city) LIKE '%đà lạt%' OR LOWER(el.name) LIKE '%đà lạt%')
  AND DATE(t.departure_time) = '2025-11-10'
ORDER BY t.departure_time;

-- 5. KIỂM TRA CỤ THỂ: Trip với startLocationId=65 và endLocationId=3
SELECT 
    t.trip_id,
    t.departure_time,
    t.status,
    r.route_id,
    r.start_location_id,
    r.end_location_id
FROM trip t
JOIN route r ON t.route_id = r.route_id
WHERE r.start_location_id = 65 
  AND r.end_location_id = 3
  AND DATE(t.departure_time) = '2025-11-10';

-- 6. Nếu không có kết quả ở query 5, kiểm tra có route nào với 65 và 3 không
SELECT 
    route_id,
    name,
    start_location_id,
    end_location_id
FROM route
WHERE start_location_id = 65 AND end_location_id = 3;

-- 7. Kiểm tra TẤT CẢ routes có Đà Nẵng làm điểm đi
SELECT 
    r.route_id,
    r.name,
    r.start_location_id,
    sl.name AS start_location_name,
    r.end_location_id,
    el.name AS end_location_name
FROM route r
JOIN location sl ON r.start_location_id = sl.location_id
JOIN location el ON r.end_location_id = el.location_id
WHERE LOWER(sl.city) LIKE '%đà nẵng%' OR LOWER(sl.name) LIKE '%đà nẵng%';

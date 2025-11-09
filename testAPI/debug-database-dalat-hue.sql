-- ============================================
-- DEBUG: Tìm chuyến Đà Lạt - Huế
-- ============================================

-- 1. Kiểm tra Location có tồn tại không
SELECT * FROM location 
WHERE location_name LIKE '%Đà Lạt%' 
   OR location_name LIKE '%Da Lat%'
   OR city LIKE '%Đà Lạt%';

SELECT * FROM location 
WHERE location_name LIKE '%Huế%' 
   OR location_name LIKE '%Hue%'
   OR city LIKE '%Huế%';

-- 2. Liệt kê TẤT CẢ locations
SELECT id, location_name, city, province FROM location ORDER BY location_name;

-- 3. Kiểm tra Route Đà Lạt - Huế
SELECT r.*, 
       l1.location_name as start_location,
       l2.location_name as end_location
FROM route r
LEFT JOIN location l1 ON r.start_location_id = l1.id
LEFT JOIN location l2 ON r.end_location_id = l2.id
WHERE l1.location_name LIKE '%Đà Lạt%' 
  AND l2.location_name LIKE '%Huế%';

-- 4. Kiểm tra Trip có status = 'scheduled'
SELECT t.*, r.*, 
       l1.location_name as start_location,
       l2.location_name as end_location,
       DATE(t.departure_time) as departure_date
FROM trip t
JOIN route r ON t.route_id = r.id
LEFT JOIN location l1 ON r.start_location_id = l1.id
LEFT JOIN location l2 ON r.end_location_id = l2.id
WHERE t.status = 'scheduled'
  AND DATE(t.departure_time) = '2025-11-09'
LIMIT 20;

-- 5. Tìm Trip Đà Lạt - Huế (nếu có)
SELECT t.*, r.*, 
       l1.location_name as start_location,
       l2.location_name as end_location,
       DATE(t.departure_time) as departure_date
FROM trip t
JOIN route r ON t.route_id = r.id
LEFT JOIN location l1 ON r.start_location_id = l1.id
LEFT JOIN location l2 ON r.end_location_id = l2.id
WHERE (l1.location_name LIKE '%Đà Lạt%' OR l1.location_name LIKE '%Da Lat%')
  AND (l2.location_name LIKE '%Huế%' OR l2.location_name LIKE '%Hue%')
  AND t.status = 'scheduled'
  AND DATE(t.departure_time) = '2025-11-09';

-- 6. Kiểm tra LocationRepository.searchByNameOrCity()
-- Query tương tự backend
SELECT * FROM location 
WHERE location_name LIKE '%Đà Lạt%' 
   OR city LIKE '%Đà Lạt%'
LIMIT 5;

SELECT * FROM location 
WHERE location_name LIKE '%Huế%' 
   OR city LIKE '%Huế%'
LIMIT 5;

-- 7. Đếm tổng số trip scheduled
SELECT COUNT(*) as total_scheduled_trips 
FROM trip 
WHERE status = 'scheduled';

-- 8. Liệt kê các tuyến đường phổ biến
SELECT l1.location_name as start_location,
       l2.location_name as end_location,
       COUNT(t.id) as trip_count
FROM trip t
JOIN route r ON t.route_id = r.id
LEFT JOIN location l1 ON r.start_location_id = l1.id
LEFT JOIN location l2 ON r.end_location_id = l2.id
WHERE t.status = 'scheduled'
GROUP BY l1.location_name, l2.location_name
ORDER BY trip_count DESC
LIMIT 10;

-- ============================================
-- INSERT DỮ LIỆU MẪU: Đà Lạt - Huế
-- ============================================

-- 1. Insert Locations (nếu chưa có)
INSERT IGNORE INTO location (id, location_name, city, province, address, latitude, longitude)
VALUES 
  (100, 'Bến xe Đà Lạt', 'Đà Lạt', 'Lâm Đồng', '01 Tô Hiến Thành, Phường 3, Đà Lạt', 11.9404, 108.4583),
  (101, 'Bến xe Huế', 'Huế', 'Thừa Thiên Huế', 'An Cựu, Huế', 16.4637, 107.5909);

-- 2. Insert Route Đà Lạt -> Huế
INSERT IGNORE INTO route (id, route_name, start_location_id, end_location_id, distance, estimated_duration, base_price, status)
VALUES 
  (50, 'Đà Lạt - Huế', 100, 101, 450, '8:00:00', 500000, 'active');

-- 3. Insert Trip ngày 9/11/2025
INSERT INTO trip (
  route_id, 
  departure_time, 
  arrival_time, 
  price_per_seat, 
  available_seats, 
  total_seats,
  status,
  operator_id,
  coach_id
)
VALUES 
  -- Chuyến 1: 8:00 sáng
  (50, '2025-11-09 08:00:00', '2025-11-09 16:00:00', 450000, 15, 30, 'scheduled', 1, 1),
  
  -- Chuyến 2: 14:00 chiều
  (50, '2025-11-09 14:00:00', '2025-11-09 22:00:00', 480000, 12, 30, 'scheduled', 1, 2),
  
  -- Chuyến 3: 20:00 tối
  (50, '2025-11-09 20:00:00', '2025-11-10 04:00:00', 420000, 20, 30, 'scheduled', 1, 3);

-- 4. Verify inserted data
SELECT 
  t.id as trip_id,
  l1.location_name as start_location,
  l2.location_name as end_location,
  t.departure_time,
  t.arrival_time,
  t.price_per_seat,
  t.available_seats,
  t.status
FROM trip t
JOIN route r ON t.route_id = r.id
JOIN location l1 ON r.start_location_id = l1.id
JOIN location l2 ON r.end_location_id = l2.id
WHERE DATE(t.departure_time) = '2025-11-09'
  AND l1.location_name LIKE '%Đà Lạt%'
  AND l2.location_name LIKE '%Huế%';

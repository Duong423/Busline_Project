-- Kiểm tra locations trong database
SELECT * FROM location;

-- Tìm locations chứa "Cần Thơ"
SELECT * FROM location 
WHERE LOWER(name) LIKE LOWER('%Cần Thơ%') 
   OR LOWER(city) LIKE LOWER('%Cần Thơ%');

-- Tìm locations chứa "Giáp Bát"
SELECT * FROM location 
WHERE LOWER(name) LIKE LOWER('%Giáp Bát%') 
   OR LOWER(city) LIKE LOWER('%Giáp Bát%');

-- Kiểm tra tất cả routes
SELECT 
    r.id,
    r.name as route_name,
    start_loc.name as start_location,
    start_loc.city as start_city,
    end_loc.name as end_location,
    end_loc.city as end_city
FROM route r
LEFT JOIN location start_loc ON r.start_location_id = start_loc.id
LEFT JOIN location end_loc ON r.end_location_id = end_loc.id;

-- Kiểm tra trips với locations
SELECT 
    t.id,
    t.departure_time,
    t.arrival_time,
    t.price_per_seat,
    t.available_seats,
    r.name as route_name,
    start_loc.name as start_location,
    start_loc.city as start_city,
    end_loc.name as end_location,
    end_loc.city as end_city
FROM trip t
LEFT JOIN route r ON t.route_id = r.id
LEFT JOIN location start_loc ON r.start_location_id = start_loc.id
LEFT JOIN location end_loc ON r.end_location_id = end_loc.id
WHERE t.status = 'scheduled'
ORDER BY t.departure_time;

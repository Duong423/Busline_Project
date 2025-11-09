# 🐛 Smart Chatbot Bug Fix - Không Tìm Thấy Kết Quả

## Vấn Đề

AI đã trích xuất được **departure**, **destination**, và **departureDate** chính xác, nhưng vẫn trả về `NEED_MORE_INFO` hoặc 0 results.

**Test case**: `"Cần Thơ đi Giáp Bát ngày 29-11"`
- ✅ AI extracted: `departure="Cần Thơ"`, `destination="Giáp Bát"`, `departureDate="2025-11-29"`
- ❌ Result: `NEED_MORE_INFO` với `trips=[]`

---

## Root Causes

### 1. **AI Model Limitation** ✅ FIXED

**Vấn đề**: 
- Model `mistral-7b-instruct:free` chỉ trả về **3 tokens** thay vì JSON đầy đủ
- Không đủ để parse thành `SearchIntentDTO`

**Giải pháp**:
```java
// BEFORE
"mistralai/mistral-7b-instruct:free" // 7B params - too weak

// AFTER  
"meta-llama/llama-3.3-70b-instruct:free" // 70B params - 10x stronger!
```

### 2. **Location Mapping Missing** ✅ FIXED

**Vấn đề**:
```java
// Danh sách cities thiếu nhiều địa danh quan trọng
String[] cities = {
    "hà nội", "sài gòn", ... // Chỉ 19 địa điểm
    // ❌ Không có: "giáp bát", "mỹ đình", "miền đông", etc.
};
```

**Giải pháp**:
```java
String[] cities = {
    // Thành phố lớn
    "hà nội", "hải phòng", "sài gòn", "tp.hcm", "hồ chí minh",
    "đà nẵng", "huế", "nha trang", "đà lạt", "vũng tàu",
    "cần thơ", "quy nhơn", "phú quốc", "hạ long", "ninh bình",
    "sapa", "hội an", "phan thiết", "buôn ma thuột",
    
    // Bến xe và địa danh quan trọng ✅
    "giáp bát", "mỹ đình", "yên nghĩa", "nước ngầm", "gia lâm",
    "miền đông", "miền tây", "bến xe miền đông", "bến xe miền tây",
    "bến xe an sương", "an sương", "chớ lớn",
    
    // Tỉnh thành khác (40+ locations)
    ...
};
```

### 3. **AI Prompt Không Đủ Context** ✅ FIXED

**Vấn đề**:
- Prompt thiếu ví dụ cụ thể về "Giáp Bát"
- AI không nhận diện được địa danh ít phổ biến

**Giải pháp**:
```java
Danh sách địa điểm ở Việt Nam (tỉnh, thành phố, bến xe):
Hà Nội, Giáp Bát ✅, Mỹ Đình, Yên Nghĩa, Gia Lâm, Nước Ngầm,
TP.HCM, Sài Gòn, Miền Đông, Miền Tây, An Sương, Chợ Lớn,
...

Trả về CHỈ JSON object. Ví dụ:

Input: "Cần Thơ đi Giáp Bát ngày 29-11" ✅
Output: {
    "intentType": "SEARCH_TRIP",
    "departure": "Cần Thơ",
    "destination": "Giáp Bát",
    "departureDate": "2025-11-29",
    "confidence": 0.9
}
```

### 4. **Location Repository Query Issue** ⚠️ POTENTIAL

**Vấn đề**:
```java
// TripSearchService.java
List<Location> locations = locationRepository.searchByNameOrCity(intent.getDeparture());
// ❌ Nếu database không có location "Cần Thơ" hoặc "Giáp Bát"
// → startLocationId = null hoặc endLocationId = null
// → Query sẽ không filter theo location → Trả về sai hoặc quá nhiều
```

**Query**:
```sql
SELECT l FROM Location l 
WHERE LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
   OR LOWER(l.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
```

**Cần kiểm tra**:
```sql
-- Xem tất cả locations trong DB
SELECT * FROM location;

-- Tìm "Cần Thơ"
SELECT * FROM location 
WHERE LOWER(name) LIKE '%cần thơ%' 
   OR LOWER(city) LIKE '%cần thơ%';

-- Tìm "Giáp Bát"  
SELECT * FROM location 
WHERE LOWER(name) LIKE '%giáp bát%' 
   OR LOWER(city) LIKE '%giáp bát%';
```

**❌ Nếu không có** → Cần insert:
```sql
INSERT INTO location (name, city, address, latitude, longitude) VALUES
('Bến xe Giáp Bát', 'Hà Nội', 'Hoàng Mai, Hà Nội', 20.9815, 105.8447),
('Bến xe Cần Thơ', 'Cần Thơ', 'Ninh Kiều, Cần Thơ', 10.0452, 105.7469);
```

### 5. **Date Filter Logic Bug** ✅ FIXED

**Vấn đề**:
```java
// BEFORE - Loại bỏ chuyến đi qua đêm
untilInstant = intent.getDepartureDate()
    .atTime(23, 59, 59) // End of day
    .atZone(ZoneId.systemDefault())
    .toInstant();

// Query: t.estimatedArrivalTime < untilTime
// ❌ Nếu chuyến xuất phát 23:00 ngày 29/11, đến 05:00 ngày 30/11
// → estimatedArrivalTime (30/11 05:00) > untilTime (29/11 23:59) 
// → BỊ LOẠI BỎ!
```

**Giải pháp Option 1** - Không dùng untilTime:
```java
List<TripFilterResponseDTO> trips = tripService.searchTrips(
    departureInstant,
    null, // ✅ Không filter theo arrival time
    intent.getNumberOfTickets(),
    startLocationId,
    endLocationId,
    TripStatus.scheduled
);

// Filter sau khi query
if (intent.getDepartureDate() != null) {
    LocalDate targetDate = intent.getDepartureDate();
    trips = trips.stream()
        .filter(trip -> {
            LocalDate tripDate = trip.getDeparture_time()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
            return tripDate.equals(targetDate); // Chỉ lấy chuyến trong ngày
        })
        .collect(Collectors.toList());
}
```

**Giải pháp Option 2** - Dùng ngày hôm sau:
```java
untilInstant = intent.getDepartureDate()
    .plusDays(1) // ✅ Ngày hôm sau
    .atStartOfDay(ZoneId.systemDefault())
    .toInstant();
// → Bao gồm chuyến đến trước 00:00 ngày hôm sau
```

### 6. **Debug Logging Added** ✅ FIXED

**TripSearchService.java**:
```java
log.info("🔍 Searching trips from database with intent: {}", intent);

// Location search
log.info("📍 Found {} locations for departure '{}': {}", 
    locations.size(), intent.getDeparture(), 
    locations.stream().map(l -> l.getId() + ":" + l.getName() + "(" + l.getCity() + ")").toList());

if (startLocationId == null) {
    log.warn("⚠️ No location found for departure: {}", intent.getDeparture());
}

// Date range
log.info("📅 Searching trips departing between: {} and {}", departureInstant, untilInstant);

// Query params
log.info("🔎 Search parameters: startLocationId={}, endLocationId={}, from={}, until={}, seats={}, status={}", 
    startLocationId, endLocationId, departureInstant, untilInstant, intent.getNumberOfTickets(), TripStatus.scheduled);

// Results
log.info("✅ Found {} trips from database", trips.size());
log.info("📅 After date filtering: {} trips on {}", trips.size(), targetDate);
```

**IntentExtractionService.java**:
```java
log.info("🤖 AI Response (raw): {}", aiResponse);
log.info("✅ Extracted intent: {}", intent);
log.warn("⚠️ AI returned empty, using regex fallback");
```

---

## Test Plan

### Step 1: Verify Database
```sql
-- Run: testAPI/debug-location-search.sql

-- Kỳ vọng:
-- ✅ Có location "Cần Thơ" (id=X)
-- ✅ Có location "Giáp Bát" hoặc "Hà Nội" (id=Y)
-- ✅ Có route từ X đến Y (hoặc tương tự)
-- ✅ Có trip với route đó, ngày 29/11/2025, status=scheduled
```

### Step 2: Test AI Extraction
```bash
# Restart app
mvn spring-boot:run

# Watch logs for:
# 🤖 AI Response (raw): { "intentType": "SEARCH_TRIP", ... }
# ✅ Extracted intent: SearchIntentDTO(intentType=SEARCH_TRIP, departure=Cần Thơ, ...)
```

### Step 3: Test Location Mapping
```http
POST http://localhost:8080/api/ai-chat/smart/send
Content-Type: application/json

{
  "userId": 1,
  "message": "Cần Thơ đi Giáp Bát ngày 29-11",
  "timestamp": 1732000000000
}

# Expected logs:
# 📍 Found 1 locations for departure 'Cần Thơ': [5:Bến xe Cần Thơ(Cần Thơ)]
# 📍 Found 1 locations for destination 'Giáp Bát': [12:Bến xe Giáp Bát(Hà Nội)]
# 🔎 Search parameters: startLocationId=5, endLocationId=12, ...
```

### Step 4: Test Database Query
```bash
# Expected logs:
# ✅ Found 0 trips from database  # ← Nếu 0, kiểm tra:
#   - Có route từ location 5 → 12?
#   - Có trip với route đó, ngày 29/11?
#   - Trip có status=scheduled?
```

### Step 5: Test Response
```json
{
  "type": "PRODUCT_SEARCH",  // ✅ Not NEED_MORE_INFO
  "content": "🎉 Tuyệt vời! Tôi tìm thấy **2 chuyến xe**...",
  "searchIntent": {
    "intentType": "SEARCH_TRIP",
    "departure": "Cần Thơ",
    "destination": "Giáp Bát",
    "departureDate": "2025-11-29"
  },
  "trips": [
    {
      "tripId": 123,
      "routeName": "Cần Thơ - Hà Nội",
      "departureTime": "2025-11-29T08:00:00",
      "price": 450000,
      ...
    }
  ],
  "totalResults": 2
}
```

---

## Checklist

- [x] Upgrade AI model: mistral-7b → llama-3.3-70b
- [x] Expand cities list: 19 → 50+ locations
- [x] Add examples to AI prompt (Giáp Bát case)
- [x] Fix date filter logic (remove untilTime or use next day)
- [x] Add date filtering after query
- [x] Add comprehensive debug logging
- [ ] Verify database has required locations
- [ ] Verify database has matching routes
- [ ] Verify database has trips on target date
- [ ] Test end-to-end flow
- [ ] Confirm response type = PRODUCT_SEARCH

---

## Next Actions

1. **Run SQL check**: `testAPI/debug-location-search.sql`
2. **Insert missing data** if needed (locations, routes, trips)
3. **Restart application**: `mvn spring-boot:run`
4. **Test API call**: See logs for location mapping
5. **Fix database** if startLocationId or endLocationId is null
6. **Verify results**: Should see trips array populated

---

## Files Changed

1. `IntentExtractionService.java`
   - Changed model to llama-3.3-70b
   - Expanded cities array
   - Enhanced AI prompt with examples
   - Added debug logging

2. `TripSearchService.java`
   - Fixed date filter (untilTime = null)
   - Added post-query date filtering
   - Added location search logging
   - Added query parameter logging

3. `testAPI/debug-location-search.sql` (new)
   - SQL queries to verify locations, routes, trips

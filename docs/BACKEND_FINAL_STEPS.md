# ✅ Backend - Bước Cuối Cùng

## 🎯 Bạn CHỈ CẦN làm 2 việc:

### 1️⃣ Config OpenRouter API Key (5 phút)

#### Bước 1: Lấy API Key miễn phí
1. Truy cập: https://openrouter.ai/
2. Click "Sign Up" (hoặc Sign in với Google/GitHub)
3. Vào **Settings** → **API Keys**
4. Click **Create Key** → Copy key (dạng: `sk-or-v1-xxx...`)

#### Bước 2: Thêm vào project
**Option A: Dùng Environment Variable (Recommended)**
```properties
# Windows PowerShell
$env:OPENROUTER_API_KEY="sk-or-v1-YOUR_KEY_HERE"

# Hoặc thêm vào System Environment Variables
```

**Option B: Thêm vào application.properties**
```properties
# src/main/resources/application.properties

# OpenRouter AI Configuration
openrouter.api.key=sk-or-v1-YOUR_KEY_HERE
openrouter.api.url=https://openrouter.ai/api/v1/chat/completions
openrouter.model=mistralai/mistral-7b-instruct:free
openrouter.max-tokens=500
openrouter.temperature=0.3
```

#### Bước 3: Kiểm tra
Chạy app và test endpoint:
```bash
mvn spring-boot:run

# Test API
curl http://localhost:8080/api/ai-chat/status
```

**Lưu ý:** Nếu không config key:
- ✅ App vẫn chạy bình thường
- ✅ Tự động fallback về regex extraction
- ⚠️ Chỉ mất tính năng AI thông minh

---

### 2️⃣ Kết nối Database Thật (30 phút)

#### Hiện tại:
Code đang dùng **mock data** trong `TripSearchService.getMockTrips()`

#### Cần làm:
Thay thế bằng **database query thật**

**File cần update:** `TripSearchService.java`

```java
// TODO: Thay thế method này
private List<TripSearchResultDTO> searchTripsFromDatabase(SearchIntentDTO intent) {
    // HIỆN TẠI: Mock data
    return getMockTrips(intent);
    
    // THAY BẰNG: Database query
    // Ví dụ với JPA/Hibernate:
}
```

#### Cách implement (Giả sử bạn đã có Trip/Route entities):

**Option 1: Dùng Repository method**
```java
@Service
@RequiredArgsConstructor
public class TripSearchService {
    
    private final TripRepository tripRepository; // Inject repository của bạn
    
    public List<TripSearchResultDTO> searchTrips(SearchIntentDTO intent) {
        // Tìm kiếm từ database
        List<Trip> trips = tripRepository.findBySearchCriteria(
            intent.getDeparture(),
            intent.getDestination(),
            intent.getDepartureDate()
        );
        
        // Convert sang DTO
        return trips.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Convert entity → DTO
    private TripSearchResultDTO convertToDTO(Trip trip) {
        return TripSearchResultDTO.builder()
            .tripId(trip.getId())
            .routeName(trip.getRoute().getName())
            .departureLocation(trip.getRoute().getDepartureLocation())
            .arrivalLocation(trip.getRoute().getArrivalLocation())
            .departureTime(trip.getDepartureTime())
            .arrivalTime(trip.getArrivalTime())
            .price(trip.getPrice())
            .availableSeats(trip.getAvailableSeats())
            .busType(trip.getBus().getType())
            .busPlate(trip.getBus().getPlateNumber())
            .rating(trip.getAverageRating())
            .hasPromotion(trip.hasActivePromotion()) // Method tự implement
            .discountedPrice(trip.getDiscountedPrice())
            .build();
    }
}
```

**Option 2: Dùng Criteria API (Linh hoạt hơn)**
```java
private List<TripSearchResultDTO> searchTripsFromDatabase(SearchIntentDTO intent) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Trip> query = cb.createQuery(Trip.class);
    Root<Trip> trip = query.from(Trip.class);
    
    List<Predicate> predicates = new ArrayList<>();
    
    // Filter theo điểm đi
    if (intent.getDeparture() != null) {
        predicates.add(cb.like(
            cb.lower(trip.get("route").get("departureLocation")), 
            "%" + intent.getDeparture().toLowerCase() + "%"
        ));
    }
    
    // Filter theo điểm đến
    if (intent.getDestination() != null) {
        predicates.add(cb.like(
            cb.lower(trip.get("route").get("arrivalLocation")), 
            "%" + intent.getDestination().toLowerCase() + "%"
        ));
    }
    
    // Filter theo ngày
    if (intent.getDepartureDate() != null) {
        predicates.add(cb.equal(
            cb.function("DATE", LocalDate.class, trip.get("departureTime")), 
            intent.getDepartureDate()
        ));
    }
    
    // Filter theo loại xe (optional)
    if (intent.getBusType() != null) {
        predicates.add(cb.equal(trip.get("bus").get("type"), intent.getBusType()));
    }
    
    query.where(predicates.toArray(new Predicate[0]));
    
    List<Trip> trips = entityManager.createQuery(query).getResultList();
    
    return trips.stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
}
```

**Option 3: Native Query (Nhanh nhất)**
```java
@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    
    @Query(value = """
        SELECT t.* FROM trips t
        JOIN routes r ON t.route_id = r.id
        WHERE LOWER(r.departure_location) LIKE LOWER(CONCAT('%', :departure, '%'))
          AND LOWER(r.arrival_location) LIKE LOWER(CONCAT('%', :destination, '%'))
          AND DATE(t.departure_time) = :departureDate
          AND t.available_seats > 0
        ORDER BY 
          CASE WHEN t.promotion_id IS NOT NULL THEN 0 ELSE 1 END,
          t.price ASC
        LIMIT 10
        """, nativeQuery = true)
    List<Trip> findBySearchCriteria(
        @Param("departure") String departure,
        @Param("destination") String destination,
        @Param("departureDate") LocalDate departureDate
    );
}
```

---

## 🚀 Sau khi hoàn thành 2 việc trên:

### ✅ Backend HOÀN TẤT 100%
- API endpoints ready
- AI integration working
- Database connected
- Mock data → Real data

### ✅ Chuyển sang Frontend
- Copy code từ `SMART_CHATBOT_FRONTEND_GUIDE.md`
- Implement components
- Test end-to-end

---

## 🧪 Test Backend

### Test 1: AI Service
```bash
# Test REST API với AI
curl -X POST http://localhost:8080/api/ai-chat/smart/send \
  -H "Content-Type: application/json" \
  -d '{"content": "Tìm vé từ Hà Nội đến Đà Nẵng ngày mai"}'
```

**Expected Response:**
```json
{
  "status": "success",
  "data": {
    "content": "Tìm thấy X chuyến...",
    "type": "PRODUCT_SEARCH",
    "trips": [...],
    "searchIntent": {
      "departure": "Hà Nội",
      "destination": "Đà Nẵng",
      "departureDate": "2025-11-10"
    }
  }
}
```

### Test 2: Database Query
```bash
# Nếu có database thật, kết quả sẽ từ DB
# Nếu chưa config, sẽ trả mock data
```

---

## ⚡ Quick Start Commands

```bash
# 1. Set API key (Windows)
$env:OPENROUTER_API_KEY="sk-or-v1-xxx"

# 2. Run backend
mvn clean spring-boot:run

# 3. Test API
curl http://localhost:8080/api/ai-chat/status

# 4. Test smart search
# Dùng file: testAPI/smart-chatbot-test.http
# Hoặc Postman/Thunder Client
```

---

## 📝 Tóm Tắt

### Backend CẦN:
1. ✅ **Config API key** (5 phút) - Optional nhưng recommended
2. ✅ **Connect database** (30 phút) - Bắt buộc nếu muốn data thật

### Backend KHÔNG CẦN:
- ❌ Không cần viết thêm service
- ❌ Không cần tạo thêm DTO
- ❌ Không cần thêm endpoint
- ❌ Không cần config CORS (đã có)
- ❌ Không cần config WebSocket (đã có)

### Sẵn sàng cho Frontend:
✅ Tất cả API endpoints đã ready
✅ DTOs đã match với frontend types
✅ Mock data để test ngay
✅ Documentation đầy đủ

---

## 🎯 Next: Frontend Implementation

Xem hướng dẫn chi tiết: **`SMART_CHATBOT_FRONTEND_GUIDE.md`**

**Chỉ cần:**
1. Copy components
2. Install dependencies
3. Connect WebSocket
4. Done! 🎉

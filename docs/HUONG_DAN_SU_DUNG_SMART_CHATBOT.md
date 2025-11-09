# 🤖 Hướng Dẫn Sử Dụng Smart Chatbot - Tìm Kiếm Chuyến Xe

## ⚠️ VẤN ĐỀ BẠN GẶP PHẢI

Bạn đang gặp vấn đề:
1. ❌ AI **không tìm kiếm trong database**
2. ❌ AI chỉ trả lời **thông tin chung chung**
3. ❌ Không có **link chuyến xe cụ thể**

**Nguyên nhân**: Bạn đang sử dụng **NHẦM ENDPOINT**!

---

## 🔍 CÓ 2 LOẠI CHATBOT

### 1. **AI Chatbot Thường** (Không tìm kiếm database) ❌

**Endpoint:**
- REST API: `POST /api/ai-chat/send`
- WebSocket: `/app/chat.ai/{userId}` → Nhận: `/topic/ai/{userId}`

**Đặc điểm:**
- ✅ Trả lời câu hỏi chung
- ❌ KHÔNG tìm kiếm trong database
- ❌ KHÔNG trả về chuyến xe cụ thể
- ❌ Chỉ đưa ra hướng dẫn chung chung

**Khi nào dùng:**
- Hỏi cách đặt vé
- Hỏi chính sách hoàn tiền
- Hỏi thông tin chung về dịch vụ

---

### 2. **Smart Chatbot** (TÌM KIẾM DATABASE) ✅

**Endpoint:**
- REST API: `POST /api/ai-chat/smart/send` ← **DÙNG CÁI NÀY!**
- WebSocket: `/app/chat.smart/{userId}` → Nhận: `/topic/smart/{userId}`

**Đặc điểm:**
- ✅ Trích xuất ý định từ chat (điểm đi, điểm đến, ngày)
- ✅ TÌM KIẾM trong database PostgreSQL
- ✅ Trả về **danh sách chuyến xe thật**
- ✅ Có **tripId, giá, ghế trống, rating**
- ✅ Có **link để đặt vé**

**Khi nào dùng:**
- Tìm vé xe
- Hỏi giá vé
- Hỏi lịch trình
- Muốn đặt vé

---

## 🚀 CÁCH SỬA LỖI CỦA BẠN

### Bước 1: Đổi Endpoint ở Frontend

**❌ SAI (đang dùng):**
```javascript
// WebSocket
stompClient.send('/app/chat.ai/' + userId, ...);
stompClient.subscribe('/topic/ai/' + userId, ...);

// Hoặc REST API
fetch('/api/ai-chat/send', { ... });
```

**✅ ĐÚNG (phải dùng):**
```javascript
// WebSocket
stompClient.send('/app/chat.smart/' + userId, ...);
stompClient.subscribe('/topic/smart/' + userId, ...);

// Hoặc REST API
fetch('/api/ai-chat/smart/send', { ... });
```

---

### Bước 2: Test Bằng HTTP File

Tạo file `test-smart-chatbot.http`:

```http
### Test Smart Chatbot - Đà Lạt đi Huế
POST http://localhost:8080/api/ai-chat/smart/send
Content-Type: application/json

{
  "sender": "test_user",
  "content": "đà lạt đi huế 9-11",
  "type": "CHAT"
}

### Kết quả mong đợi:
# {
#   "content": "🎉 Tuyệt vời! Tôi tìm thấy **3 chuyến xe** từ **Đà Lạt** đến **Huế** vào ngày **2025-11-09**...",
#   "type": "PRODUCT_SEARCH",
#   "trips": [
#     {
#       "tripId": 123,
#       "routeName": "Đà Lạt - Huế",
#       "departureLocation": "Đà Lạt",
#       "arrivalLocation": "Huế",
#       "departureTime": "2025-11-09T08:00:00",
#       "price": 450000.0,
#       "availableSeats": 12,
#       "busType": "VIP",
#       "rating": 4.5,
#       "imageUrl": "https://..."
#     }
#   ],
#   "totalResults": 3,
#   "suggestedQuestions": [
#     "Chuyến nào rẻ nhất?",
#     "Xe VIP có không?"
#   ]
# }
```

---

## 📋 SO SÁNH 2 LOẠI RESPONSE

### AI Chatbot Thường (SAI) ❌

**Request:**
```json
POST /api/ai-chat/send
{
  "sender": "user123",
  "content": "đà lạt đi huế 9-11"
}
```

**Response:**
```json
{
  "id": 1,
  "content": "Để đặt vé xe từ Đà Lạt đi Huế trong ngày 9-11, bạn có thể thực hiện theo các bước đơn giản sau:...",
  "sender": "AI Bot",
  "type": "CHAT"
}
```

**Vấn đề:**
- ❌ Không có thông tin chuyến xe
- ❌ Không có giá
- ❌ Không có link đặt vé
- ❌ Chỉ hướng dẫn chung chung

---

### Smart Chatbot (ĐÚNG) ✅

**Request:**
```json
POST /api/ai-chat/smart/send
{
  "sender": "user123",
  "content": "đà lạt đi huế 9-11"
}
```

**Response:**
```json
{
  "content": "🎉 Tuyệt vời! Tôi tìm thấy **3 chuyến xe** từ **Đà Lạt** đến **Huế** vào ngày **2025-11-09**.\n\n🚌 **Chuyến 1**: VIP - Khởi hành lúc 08:00\n   💰 Giá: **450,000đ**\n   🪑 Còn 12 ghế trống\n   ⭐ Đánh giá: 4.5/5\n\nBạn có thể chọn chuyến phù hợp và đặt vé ngay nhé! 🎫",
  "type": "PRODUCT_SEARCH",
  "searchIntent": {
    "intentType": "SEARCH_TRIP",
    "departure": "Đà Lạt",
    "destination": "Huế",
    "departureDate": "2025-11-09",
    "confidence": 0.95
  },
  "trips": [
    {
      "tripId": 123,
      "routeName": "Đà Lạt - Huế",
      "departureLocation": "Đà Lạt",
      "arrivalLocation": "Huế",
      "departureTime": "2025-11-09T08:00:00",
      "arrivalTime": "2025-11-09T20:00:00",
      "price": 450000.0,
      "availableSeats": 12,
      "busType": "VIP",
      "busPlate": "Nhà xe ABC",
      "amenities": ["WiFi", "Điều hòa", "Nước uống"],
      "rating": 4.5,
      "hasPromotion": false,
      "imageUrl": "https://..."
    }
  ],
  "totalResults": 3,
  "needMoreInfo": false,
  "suggestedQuestions": [
    "Chuyến nào rẻ nhất?",
    "Xe VIP có không?",
    "Còn ghế trống không?"
  ],
  "timestamp": 1762677001908
}
```

**Ưu điểm:**
- ✅ Có **tripId** để tạo link: `/booking/{tripId}`
- ✅ Có **giá tiền thật** từ database
- ✅ Có **số ghế trống**
- ✅ Có **rating, amenities**
- ✅ Có **thông tin chi tiết** để đặt vé ngay

---

## 🔧 CODE FRONTEND ĐÚNG

### WebSocket Connection

```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

// Kết nối WebSocket
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  console.log('✅ Connected to Smart Chatbot');
  
  // Subscribe nhận response
  stompClient.subscribe('/topic/smart/' + userId, (message) => {
    const response = JSON.parse(message.body);
    handleSmartResponse(response);
  });
});

// Gửi tin nhắn
function sendMessage(userMessage) {
  const payload = {
    sender: userId,
    content: userMessage,
    type: 'CHAT',
    timestamp: Date.now()
  };
  
  stompClient.send(
    '/app/chat.smart/' + userId,  // ← Endpoint ĐÚNG
    {},
    JSON.stringify(payload)
  );
}

// Xử lý response
function handleSmartResponse(response) {
  console.log('Response type:', response.type);
  
  if (response.type === 'PRODUCT_SEARCH') {
    // Có kết quả tìm kiếm
    displaySearchResults(response.trips);
    
    // Tạo link đặt vé
    response.trips.forEach(trip => {
      const bookingUrl = `/booking/${trip.tripId}`;
      console.log('Booking URL:', bookingUrl);
    });
  }
}
```

---

### REST API Call

```javascript
async function searchTrips(userMessage) {
  const response = await fetch('http://localhost:8080/api/ai-chat/smart/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify({
      sender: userId,
      content: userMessage,
      type: 'CHAT'
    })
  });
  
  const data = await response.json();
  
  if (data.data.type === 'PRODUCT_SEARCH') {
    // Hiển thị kết quả
    data.data.trips.forEach(trip => {
      console.log(`Trip ${trip.tripId}: ${trip.routeName}`);
      console.log(`Price: ${trip.price}đ`);
      console.log(`Booking: /booking/${trip.tripId}`);
    });
  }
  
  return data;
}

// Sử dụng
searchTrips("đà lạt đi huế 9-11");
```

---

## 📊 RESPONSE TYPE

Smart Chatbot có 5 loại response:

### 1. PRODUCT_SEARCH (Có kết quả)
```json
{
  "type": "PRODUCT_SEARCH",
  "trips": [...],  // Có chuyến xe
  "totalResults": 3
}
```
→ **Hiển thị danh sách chuyến, cho phép đặt vé**

---

### 2. NEED_MORE_INFO (Thiếu thông tin)
```json
{
  "type": "NEED_MORE_INFO",
  "content": "Để tìm kiếm chuyến đi phù hợp, bạn vui lòng cho tôi biết:\n📍 Điểm đi của bạn là đâu?",
  "needMoreInfo": true
}
```
→ **Hỏi thêm thông tin từ user**

---

### 3. TEXT (Câu trả lời thường)
```json
{
  "type": "TEXT",
  "content": "Xin chào! Tôi có thể giúp bạn tìm vé xe...",
  "trips": []
}
```
→ **Hiển thị text bình thường**

---

### 4. BOOKING_GUIDE (Hướng dẫn đặt vé)
```json
{
  "type": "BOOKING_GUIDE",
  "content": "Để đặt vé xe trên Busify, bạn làm theo các bước sau:..."
}
```
→ **Hiển thị hướng dẫn**

---

### 5. ERROR (Lỗi)
```json
{
  "type": "ERROR",
  "content": "Xin lỗi, đã có lỗi xảy ra..."
}
```
→ **Hiển thị thông báo lỗi**

---

## 🧪 TEST CASES

### Test 1: Tìm kiếm cơ bản
```
User: "đà lạt đi huế 9-11"
AI trích xuất:
  - Điểm đi: Đà Lạt
  - Điểm đến: Huế
  - Ngày: 2025-11-09
→ Gọi database → Trả về danh sách chuyến
```

### Test 2: Thiếu thông tin
```
User: "tôi muốn đi huế"
AI trích xuất:
  - Điểm đi: null
  - Điểm đến: Huế
→ Response type: NEED_MORE_INFO
→ AI hỏi: "Bạn xuất phát từ đâu?"
```

### Test 3: Hỏi giá
```
User: "giá vé đà lạt huế bao nhiêu"
AI trích xuất:
  - Intent: ASK_PRICE
  - Điểm đi: Đà Lạt
  - Điểm đến: Huế
→ Tìm chuyến → Trả về thông tin giá
```

---

## 🔗 TẠO LINK ĐẶT VÉ

Sau khi có `tripId`, tạo link:

```javascript
function createBookingLink(trip) {
  return {
    // Link đặt vé
    bookingUrl: `/booking/${trip.tripId}`,
    
    // Link chi tiết chuyến
    detailUrl: `/trips/${trip.tripId}`,
    
    // Deep link cho app
    deepLink: `busify://trip/${trip.tripId}`,
    
    // QR code
    qrCodeData: JSON.stringify({
      tripId: trip.tripId,
      price: trip.price,
      date: trip.departureTime
    })
  };
}
```

---

## ✅ CHECKLIST

- [ ] **Frontend đã đổi endpoint** sang `/api/ai-chat/smart/send`
- [ ] **WebSocket subscribe** đúng `/topic/smart/{userId}`
- [ ] **WebSocket send** đúng `/app/chat.smart/{userId}`
- [ ] **Database có dữ liệu** Đà Lạt - Huế
- [ ] **Location names** khớp với database
- [ ] **Test bằng HTTP file** trước
- [ ] **Xử lý response type** PRODUCT_SEARCH
- [ ] **Hiển thị trips[]** với link đặt vé

---

## 🐛 TROUBLESHOOTING

### Vấn đề 1: Vẫn không tìm thấy chuyến
**Kiểm tra:**
```sql
-- Kiểm tra dữ liệu location
SELECT * FROM location WHERE location_name LIKE '%Đà Lạt%';
SELECT * FROM location WHERE location_name LIKE '%Huế%';

-- Kiểm tra trips
SELECT t.*, r.* 
FROM trip t 
JOIN route r ON t.route_id = r.id
WHERE r.start_location_id = (SELECT id FROM location WHERE location_name LIKE '%Đà Lạt%')
  AND r.end_location_id = (SELECT id FROM location WHERE location_name LIKE '%Huế%')
  AND t.status = 'scheduled'
  AND DATE(t.departure_time) = '2025-11-09';
```

### Vấn đề 2: AI không trích xuất đúng
**Kiểm tra log:**
```
2025-11-09 15:30:01 INFO  IntentExtractionService - Extracting search intent from message: đà lạt đi huế 9-11
2025-11-09 15:30:02 INFO  IntentExtractionService - Extracted intent: SearchIntentDTO(intentType=SEARCH_TRIP, departure=Đà Lạt, destination=Huế, departureDate=2025-11-09, ...)
```

### Vấn đề 3: Timestamp error
**Đã fix:** ChatMessageDTO.timestamp đổi từ LocalDateTime → Long

---

## 📞 TÓM TẮT

**❌ Đang dùng SAI:**
- Endpoint: `/api/ai-chat/send`
- WebSocket: `/app/chat.ai/{userId}`
- Kết quả: Không tìm database, chỉ trả lời chung chung

**✅ PHẢI DÙNG:**
- Endpoint: `/api/ai-chat/smart/send` ← **QUAN TRỌNG!**
- WebSocket: `/app/chat.smart/{userId}`
- Kết quả: Tìm database, trả về trips[], có link đặt vé

**Chỉ cần đổi 1 dòng code là xong!** 🎉

# 🤖 Smart Chatbot - Quick Start

## 📝 Tóm Tắt

Hệ thống chatbot thông minh giúp khách hàng **tìm kiếm và đặt vé xe** qua chat, sử dụng AI miễn phí để hiểu ngôn ngữ tự nhiên.

---

## 🎯 Tính Năng Chính

- ✅ **AI hiểu ngôn ngữ tự nhiên** - "Tìm vé đi Đà Nẵng ngày mai"
- ✅ **Trích xuất thông tin tự động** - Điểm đi, điểm đến, ngày, số vé, loại xe
- ✅ **Tìm kiếm sản phẩm thông minh** - Filter, sort, recommend
- ✅ **Hiển thị kết quả trong chat** - Product cards đẹp mắt
- ✅ **Real-time WebSocket** - Trải nghiệm mượt mà
- ✅ **Fallback mechanism** - Hoạt động ngay cả khi AI offline

---

## 🚀 Quick Start

### Backend (Đã làm xong ✅)

Files đã tạo:
```
src/main/java/com/busify/project/chat/
├── dto/
│   ├── SearchIntentDTO.java          ✅
│   ├── TripSearchResultDTO.java      ✅
│   └── AIResponseDTO.java            ✅
├── service/
│   ├── IntentExtractionService.java  ✅
│   ├── TripSearchService.java        ✅
│   └── SmartChatBotService.java      ✅
└── controller/
    └── ChatAIController.java         ✅ (đã update)
```

**Cần làm:**
1. Config OpenRouter API key (miễn phí)
2. Connect với database thật (hiện đang dùng mock data)

### Frontend (Cần triển khai)

Xem hướng dẫn chi tiết: **`docs/SMART_CHATBOT_FRONTEND_GUIDE.md`**

**Steps:**
```bash
# 1. Install dependencies
npm install @stomp/stompjs sockjs-client framer-motion date-fns

# 2. Copy components từ guide
# 3. Config .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080

# 4. Run
npm run dev
```

---

## 📡 API Endpoints

### REST API
```
POST /api/ai-chat/smart/send
- Body: { "content": "Tìm vé đi Đà Nẵng" }
- Response: AIResponseDTO với trips[]
```

### WebSocket
```
Connect: ws://localhost:8080/ws
Subscribe: /topic/smart/{userId}
Send: /app/chat.smart/{userId}
```

---

## 💡 Example Usage

**Input:**
```
"Tìm vé từ Hà Nội đến Đà Nẵng ngày 15/11, 2 vé VIP"
```

**Output:**
```json
{
  "content": "Tìm thấy 5 chuyến xe...",
  "type": "PRODUCT_SEARCH",
  "searchIntent": {
    "departure": "Hà Nội",
    "destination": "Đà Nẵng",
    "departureDate": "2025-11-15",
    "numberOfTickets": 2,
    "busType": "VIP"
  },
  "trips": [
    {
      "tripId": 1,
      "price": 350000,
      "discountedPrice": 315000,
      "hasPromotion": true,
      ...
    }
  ],
  "totalResults": 5,
  "suggestedQuestions": [
    "Chuyến nào rẻ nhất?",
    "Xe có WiFi không?"
  ]
}
```

---

## 🔧 Configuration

### 1. OpenRouter API (Free AI)

```properties
# application.properties
openrouter.api.key=sk-or-v1-YOUR_KEY_HERE
openrouter.model=mistralai/mistral-7b-instruct:free
```

**Lấy key miễn phí:**
- Truy cập: https://openrouter.ai/
- Sign up → Settings → API Keys
- Free tier: $5 credit

### 2. Database Connection

Update `TripSearchService.java`:
```java
// TODO: Replace mock data với database queries
List<Trip> trips = tripRepository.findBySearchCriteria(...);
```

---

## 📚 Documentation

1. **`SMART_CHATBOT_IMPLEMENTATION_GUIDE.md`** - Hướng dẫn tổng quan, kiến trúc, troubleshooting
2. **`SMART_CHATBOT_FRONTEND_GUIDE.md`** - Hướng dẫn triển khai Next.js chi tiết

---

## ✅ Testing

```bash
# 1. Start backend
mvn spring-boot:run

# 2. Test REST API
curl -X POST http://localhost:8080/api/ai-chat/smart/send \
  -H "Content-Type: application/json" \
  -d '{"content": "Tìm vé đi Đà Nẵng"}'

# 3. Test WebSocket (dùng frontend)
npm run dev
# Open http://localhost:3000/chat
```

---

## 🎨 UI Preview

Chat window sẽ hiển thị:
- 💬 Message bubbles (user & bot)
- 🎫 Product cards với ảnh, giá, rating
- 🎁 Promotion badges
- 💡 Suggested questions
- ⚡ Real-time typing indicator

---

## 🚀 Next Steps

- [ ] Config OpenRouter API key
- [ ] Deploy backend
- [ ] Implement frontend components
- [ ] Connect với database thật
- [ ] Test end-to-end
- [ ] Deploy production

---

## 🤝 Support

Gặp vấn đề? Xem:
- Troubleshooting section trong `SMART_CHATBOT_IMPLEMENTATION_GUIDE.md`
- Check logs: `log.info()` statements
- Test với mock data trước

---

**Happy Coding! 🎉**

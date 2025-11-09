# 🚀 Hướng Dẫn Triển Khai Smart Chatbot - Tổng Quan

## 📖 Giới Thiệu

Hệ thống **Smart Chatbot** giúp khách hàng tìm kiếm và đặt vé xe thông qua cuộc trò chuyện tự nhiên. Bot sử dụng AI miễn phí (OpenRouter) để:

1. ✅ **Hiểu ý định người dùng** từ câu chat
2. ✅ **Trích xuất thông tin** (điểm đi, điểm đến, ngày, số vé...)
3. ✅ **Tìm kiếm sản phẩm** phù hợp từ database
4. ✅ **Hiển thị kết quả** ngay trong chat
5. ✅ **Hỗ trợ đặt vé** trực tiếp

---

## 🏗️ Kiến Trúc Hệ Thống

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Frontend  │◄───────►│   Backend    │◄───────►│   OpenAI    │
│  (Next.js)  │ WebSocket│ (Spring Boot)│   API   │ (OpenRouter)│
└─────────────┘         └──────────────┘         └─────────────┘
       │                        │
       │                        │
       ▼                        ▼
┌─────────────┐         ┌──────────────┐
│  Chat UI    │         │   Database   │
│  + Products │         │   (Trips)    │
└─────────────┘         └──────────────┘
```

---

## 🔄 Luồng Hoạt Động

### 1️⃣ User gửi tin nhắn
```
"Tìm vé từ Hà Nội đến Đà Nẵng ngày mai, 2 vé VIP"
```

### 2️⃣ Backend xử lý
```java
// IntentExtractionService
SearchIntent intent = extractSearchIntent(message);
// → departure: "Hà Nội"
// → destination: "Đà Nẵng"
// → date: 2025-11-10
// → tickets: 2
// → busType: "VIP"
```

### 3️⃣ Tìm kiếm database
```java
// TripSearchService
List<Trip> trips = searchTrips(intent);
// → Tìm thấy 5 chuyến phù hợp
```

### 4️⃣ AI tạo phản hồi
```java
// SmartChatBotService
AIResponse response = {
  content: "Tìm thấy 5 chuyến...",
  type: "PRODUCT_SEARCH",
  trips: [trip1, trip2, ...],
  suggestedQuestions: [...]
}
```

### 5️⃣ Frontend hiển thị
```typescript
// ChatWindow.tsx
<MessageBubble message={botMessage}>
  <SearchResults trips={response.trips} />
</MessageBubble>
```

---

## 📁 Cấu Trúc Files Đã Tạo

### Backend (Spring Boot)

```
src/main/java/com/busify/project/chat/
├── dto/
│   ├── SearchIntentDTO.java          ✅ Ý định tìm kiếm
│   ├── TripSearchResultDTO.java      ✅ Kết quả tìm kiếm
│   └── AIResponseDTO.java            ✅ Response từ AI
├── service/
│   ├── IntentExtractionService.java  ✅ Trích xuất ý định bằng AI
│   ├── TripSearchService.java        ✅ Tìm kiếm chuyến đi
│   └── SmartChatBotService.java      ✅ Điều phối toàn bộ
└── controller/
    └── ChatAIController.java         ✅ API endpoints
```

### Frontend (Next.js)

Xem chi tiết trong file: **`SMART_CHATBOT_FRONTEND_GUIDE.md`**

```
src/
├── types/chat.types.ts
├── services/websocketService.ts
├── components/chat/
│   ├── ChatWindow.tsx
│   ├── MessageBubble.tsx
│   ├── TripCard.tsx
│   └── SearchResults.tsx
└── hooks/useChatAPI.ts
```

---

## 🔧 Cài Đặt & Cấu Hình

### 1. Backend Setup

#### a) Thêm dependency (nếu chưa có)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

#### b) Cấu hình OpenRouter API

Tạo file `.env` hoặc thêm vào `application.properties`:

```properties
# OpenRouter API (Miễn phí)
openrouter.api.key=sk-or-v1-YOUR_API_KEY_HERE
openrouter.api.url=https://openrouter.ai/api/v1/chat/completions
openrouter.model=mistralai/mistral-7b-instruct:free

# Hoặc dùng model khác (free)
# openrouter.model=google/gemma-7b-it:free
# openrouter.model=meta-llama/llama-3-8b-instruct:free
```

**Lấy API Key miễn phí:**
1. Truy cập: https://openrouter.ai/
2. Đăng ký tài khoản
3. Vào Settings → API Keys
4. Tạo key mới (free tier: $5 credit)

#### c) Update OpenAIService

File `OpenAIService.java` đã có sẵn, chỉ cần đảm bảo:

```java
@Value("${openrouter.api.key}")
private String apiKey;
```

---

### 2. Frontend Setup

Xem chi tiết trong: **`SMART_CHATBOT_FRONTEND_GUIDE.md`**

```bash
# 1. Install dependencies
npm install @stomp/stompjs sockjs-client framer-motion
npm install date-fns react-markdown

# 2. Create .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080

# 3. Copy components từ guide
# 4. Run development server
npm run dev
```

---

## 📡 API Endpoints

### WebSocket (Recommended)

**Connect:**
```
ws://localhost:8080/ws
```

**Subscribe:**
```javascript
client.subscribe('/topic/smart/{userId}', callback);
```

**Send Message:**
```javascript
client.publish({
  destination: '/app/chat.smart/{userId}',
  body: JSON.stringify({ content: 'message', sender: 'user@email.com' })
});
```

### REST API (Alternative)

**POST** `/api/ai-chat/smart/send`

Request:
```json
{
  "content": "Tìm vé từ Hà Nội đến Đà Nẵng"
}
```

Response:
```json
{
  "status": "success",
  "data": {
    "content": "Tìm thấy 5 chuyến...",
    "type": "PRODUCT_SEARCH",
    "searchIntent": {
      "intentType": "SEARCH_TRIP",
      "departure": "Hà Nội",
      "destination": "Đà Nẵng"
    },
    "trips": [
      {
        "tripId": 1,
        "routeName": "Hà Nội - Đà Nẵng",
        "price": 350000,
        "availableSeats": 12,
        ...
      }
    ],
    "totalResults": 5,
    "suggestedQuestions": [
      "Chuyến nào rẻ nhất?",
      "Xe VIP có không?"
    ]
  }
}
```

---

## 🎯 Tính Năng Chính

### ✅ 1. Intent Recognition (Nhận diện ý định)

Chatbot tự động nhận diện các loại câu hỏi:

- **SEARCH_TRIP**: "Tìm vé đi Đà Nẵng"
- **ASK_PRICE**: "Giá vé Hà Nội - Sài Gòn bao nhiêu?"
- **ASK_SCHEDULE**: "Xe đi mấy giờ?"
- **BOOK_TICKET**: "Đặt vé cho tôi"
- **GENERAL_QUESTION**: Câu hỏi chung

### ✅ 2. Information Extraction (Trích xuất thông tin)

Tự động trích xuất:
- 📍 Điểm đi, điểm đến
- 📅 Ngày đi (hôm nay, ngày mai, dd/mm/yyyy)
- 🎫 Số lượng vé
- 🚌 Loại xe (VIP, giường nằm, thường)
- 💰 Khoảng giá

### ✅ 3. Smart Search (Tìm kiếm thông minh)

- Filter theo nhiều tiêu chí
- Sắp xếp theo giá, rating
- Ưu tiên xe có khuyến mãi
- Giới hạn kết quả (top 10)

### ✅ 4. Rich Responses (Phản hồi đa dạng)

- **Text only**: Câu trả lời thông thường
- **Product cards**: Hiển thị danh sách chuyến xe
- **Booking guide**: Hướng dẫn đặt vé
- **Need more info**: Yêu cầu thêm thông tin
- **Suggested questions**: Gợi ý câu hỏi tiếp theo

---

## 🔍 Ví Dụ Sử Dụng

### Example 1: Tìm kiếm cơ bản

**User:** "Tìm vé đi Đà Nẵng"

**Bot Response:**
```
Để tìm kiếm chuyến đi phù hợp, bạn vui lòng cho tôi biết:

📍 Điểm đi của bạn là đâu?
📅 Bạn muốn đi vào ngày nào?

Ví dụ: Tôi muốn đi từ Hà Nội đến Đà Nẵng vào ngày mai
```

### Example 2: Tìm kiếm đầy đủ

**User:** "Tìm vé từ Hà Nội đến Đà Nẵng ngày 15/11, 2 vé VIP"

**Bot Response:**
```
🎉 Tuyệt vời! Tôi tìm thấy 5 chuyến xe từ Hà Nội đến Đà Nẵng vào ngày 15/11/2025.

🚌 Chuyến 1: VIP - Khởi hành lúc 08:00
   💰 Giá: ~~350,000đ~~ → 315,000đ 🎁
   🪑 Còn 12 ghế trống
   ⭐ Đánh giá: 4.5/5

🚌 Chuyến 2: VIP - Khởi hành lúc 10:30
   💰 Giá: 300,000đ
   🪑 Còn 8 ghế trống
   ⭐ Đánh giá: 4.2/5

[Product Cards hiển thị ở đây]

Bạn có thể chọn chuyến phù hợp và đặt vé ngay nhé! 🎫

Gợi ý:
- Chuyến nào rẻ nhất?
- Xe có WiFi không?
- Làm sao đặt vé?
```

### Example 3: Hỏi giá

**User:** "Giá vé Hà Nội Sài Gòn bao nhiêu?"

**Bot Response:**
```
💰 Thông tin giá vé từ Hà Nội đến Sài Gòn:

• Giá thấp nhất: 280,000đ
• Giá cao nhất: 500,000đ
• Có 3 chuyến đang có khuyến mãi 🎁

Tôi đã tìm thấy 8 chuyến phù hợp. Bạn muốn xem chi tiết không?
```

---

## 🛠️ Troubleshooting

### Issue 1: AI không trả về kết quả

**Nguyên nhân:** API key không hợp lệ hoặc hết credit

**Giải pháp:**
```java
// Service tự động fallback về regex extraction
// Kiểm tra log:
log.info("AI response: {}", response);

// Test fallback:
SearchIntent intent = extractIntentWithRegex(message);
```

### Issue 2: Không tìm thấy chuyến xe

**Nguyên nhân:** Database chưa có dữ liệu hoặc điều kiện quá strict

**Giải pháp:**
```java
// 1. Kiểm tra mock data
List<Trip> mockTrips = getMockTrips(intent);

// 2. Thêm log debug
log.info("Searching with: departure={}, destination={}", 
    intent.getDeparture(), intent.getDestination());

// 3. Relax search criteria
```

### Issue 3: WebSocket không kết nối

**Nguyên nhân:** CORS hoặc WebSocket config

**Giải pháp:**
```java
// WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

---

## 📊 Monitoring & Analytics

### Track Intent Types

```java
@Service
public class ChatAnalyticsService {
    
    private final Map<String, Integer> intentCounts = new ConcurrentHashMap<>();
    
    public void trackIntent(String intentType) {
        intentCounts.merge(intentType, 1, Integer::sum);
    }
    
    public Map<String, Integer> getStats() {
        return new HashMap<>(intentCounts);
    }
}
```

### Track Search Success Rate

```java
public void trackSearch(SearchIntent intent, int resultsFound) {
    if (resultsFound > 0) {
        metrics.incrementCounter("search.success");
    } else {
        metrics.incrementCounter("search.no_results");
        log.warn("No results for: {}", intent);
    }
}
```

---

## 🚀 Optimization Tips

### 1. Cache Popular Routes

```java
@Cacheable(value = "popularRoutes", key = "#departure + '-' + #destination")
public List<Trip> searchTrips(String departure, String destination) {
    // ...
}
```

### 2. Limit AI Token Usage

```java
// Giới hạn history messages
int historyLimit = Math.min(chatHistory.size(), 5); // Chỉ lấy 5 tin gần nhất
```

### 3. Pre-compute Common Queries

```java
@Scheduled(cron = "0 0 * * * *") // Every hour
public void precomputePopularSearches() {
    List<String> popularRoutes = List.of(
        "Hà Nội-Đà Nẵng",
        "Hà Nội-Sài Gòn",
        "Đà Nẵng-Sài Gòn"
    );
    // Cache results
}
```

---

## 📈 Future Enhancements

### Phase 2
- [ ] Voice input/output
- [ ] Multi-language support
- [ ] Context awareness (remember user preferences)
- [ ] Booking integration (direct booking in chat)
- [ ] Payment in chat

### Phase 3
- [ ] Sentiment analysis
- [ ] Recommendation engine
- [ ] A/B testing different AI models
- [ ] Custom training data
- [ ] Analytics dashboard

---

## 📚 Resources

### AI Models (Free)
- **OpenRouter**: https://openrouter.ai/
- **Hugging Face**: https://huggingface.co/
- **Ollama (Local)**: https://ollama.ai/

### Documentation
- Spring WebSocket: https://docs.spring.io/spring-framework/reference/web/websocket.html
- STOMP.js: https://stomp-js.github.io/
- Next.js: https://nextjs.org/docs

---

## ✅ Checklist Triển Khai

### Backend
- [x] Tạo DTOs (SearchIntent, TripSearchResult, AIResponse)
- [x] Implement IntentExtractionService
- [x] Implement TripSearchService
- [x] Implement SmartChatBotService
- [x] Update ChatAIController
- [ ] Config OpenRouter API key
- [ ] Test với real database
- [ ] Deploy

### Frontend
- [ ] Install dependencies
- [ ] Create types
- [ ] Implement WebSocket service
- [ ] Create ChatWindow component
- [ ] Create TripCard component
- [ ] Create MessageBubble component
- [ ] Test WebSocket connection
- [ ] Style with Tailwind
- [ ] Deploy

### Testing
- [ ] Test intent extraction với các câu khác nhau
- [ ] Test tìm kiếm với database thật
- [ ] Test WebSocket real-time
- [ ] Test error handling
- [ ] Test trên mobile
- [ ] Load testing

---

## 🎉 Kết Luận

Bạn đã có đầy đủ code để triển khai **Smart Chatbot** với khả năng:

✅ **Backend hoàn chỉnh** - Trích xuất ý định, tìm kiếm sản phẩm, AI integration
✅ **Frontend components** - Chat UI, Product cards, WebSocket
✅ **Fallback mechanism** - Vẫn hoạt động khi AI offline
✅ **Free AI integration** - Sử dụng OpenRouter miễn phí

**Next Steps:**
1. Config OpenRouter API key
2. Copy frontend components
3. Test end-to-end
4. Deploy & monitor

**Chúc bạn thành công! 🚀**

---

📝 **Note:** Để triển khai production, nhớ:
- Thêm rate limiting
- Implement proper authentication
- Monitor AI API usage
- Set up error tracking (Sentry)
- Optimize database queries
- Add caching layer

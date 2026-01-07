# 🔄 Hướng Dẫn Hiển Thị Chuyến Khứ Hồi Trong Chat - Frontend

> **Ngày cập nhật**: 7/1/2026  
> **Mô tả**: Hướng dẫn cập nhật frontend để hiển thị cả chuyến đi và chuyến về trong chatbot

---

## 📋 Thay Đổi Backend

Backend đã được cập nhật để trả về **returnTrips** trong API response:

### API Response Structure (Mới)

```json
{
  "code": 200,
  "message": "Tin nhắn đã được xử lý thành công",
  "result": {
    "messageId": null,
    "content": "🎉 Tuyệt vời! Tôi tìm thấy 2 chuyến xe...",
    "type": "PRODUCT_SEARCH",
    "searchIntent": {
      "intentType": "SEARCH_TRIP",
      "departure": "Đà Nẵng",
      "destination": "Đà Lạt",
      "departureDate": "2025-11-10",
      "returnDate": "2025-11-15",
      "isRoundTrip": true,
      "confidence": 0.9
    },
    "trips": [
      // Danh sách chuyến ĐI (outbound trips)
      {
        "tripId": 64,
        "routeName": "Đà Nẵng - Đà Lạt",
        "departureLocation": "Bến xe trung tâm Đà Nẵng",
        "arrivalLocation": "Bến xe Đà Lạt",
        "departureTime": "2025-11-10T08:00:00",
        "arrivalTime": "2025-11-10T16:00:00",
        "price": 350000,
        "availableSeats": 15,
        "busType": "VIP",
        "hasPromotion": false
      }
    ],
    "returnTrips": [
      // Danh sách chuyến VỀ (return trips) - MỚI ✨
      {
        "tripId": 72,
        "routeName": "Đà Lạt - Đà Nẵng",
        "departureLocation": "Bến xe Đà Lạt",
        "arrivalLocation": "Bến xe trung tâm Đà Nẵng",
        "departureTime": "2025-11-15T09:00:00",
        "arrivalTime": "2025-11-15T17:00:00",
        "price": 350000,
        "availableSeats": 20,
        "busType": "VIP",
        "hasPromotion": false
      }
    ],
    "totalResults": 2,
    "returnTotalResults": 3,
    "needMoreInfo": false,
    "suggestedQuestions": [
      "Làm sao chọn ghế?",
      "Thanh toán thế nào?"
    ]
  }
}
```

---

## 🎨 Cập Nhật Frontend

### 1. Cập Nhật TypeScript Interface

```typescript
// types/chat.types.ts

export interface AIResponse {
  messageId?: number;
  content: string;
  type: AIResponseType;
  searchIntent?: SearchIntent;
  
  // Chuyến đi (outbound)
  trips?: TripSearchResult[];
  totalResults?: number;
  
  // Chuyến về (return) - MỚI ✨
  returnTrips?: TripSearchResult[];
  returnTotalResults?: number;
  
  needMoreInfo?: boolean;
  suggestedQuestions?: string[];
  timestamp: number;
}

export interface SearchIntent {
  intentType: string;
  departure?: string;
  destination?: string;
  departureDate?: string;
  returnDate?: string;        // MỚI ✨
  isRoundTrip?: boolean;      // MỚI ✨
  numberOfTickets?: number;
  busType?: string;
  priceMin?: number;
  priceMax?: number;
  confidence?: number;
}
```

### 2. Cập Nhật MessageBubble Component

```jsx
// components/chat/MessageBubble.jsx

function MessageBubble({ message }) {
  if (message.sender === 'user') {
    return <div className="user-message">{message.content}</div>;
  }

  // Bot message - check type
  switch (message.type) {
    case 'PRODUCT_SEARCH':
      return (
        <div className="bot-message">
          <div className="text">{message.content}</div>
          
          {/* Chuyến đi (Outbound) */}
          {message.trips && message.trips.length > 0 && (
            <div className="trip-section">
              <h4 className="trip-section-title">
                📤 Chiều đi {message.searchIntent?.departureDate && 
                  `(${formatDate(message.searchIntent.departureDate)})`
                }
              </h4>
              <TripList trips={message.trips} tripType="outbound" />
            </div>
          )}
          
          {/* Chuyến về (Return) - MỚI ✨ */}
          {message.returnTrips && message.returnTrips.length > 0 && (
            <div className="trip-section">
              <h4 className="trip-section-title">
                📥 Chiều về {message.searchIntent?.returnDate && 
                  `(${formatDate(message.searchIntent.returnDate)})`
                }
              </h4>
              <TripList trips={message.returnTrips} tripType="return" />
            </div>
          )}
          
          {/* Hiển thị tổng giá khứ hồi nếu có cả 2 chiều */}
          {message.trips && message.returnTrips && 
           message.trips.length > 0 && message.returnTrips.length > 0 && (
            <div className="round-trip-summary">
              <p className="total-price">
                💵 <strong>Tổng giá khứ hồi (ước tính):</strong>{' '}
                {formatPrice(
                  (message.trips[0].discountedPrice || message.trips[0].price) +
                  (message.returnTrips[0].discountedPrice || message.returnTrips[0].price)
                )}
              </p>
            </div>
          )}
          
          <SuggestedQuestions questions={message.suggestedQuestions} />
        </div>
      );

    case 'NEED_MORE_INFO':
      return (
        <div className="bot-message info">
          <div className="text">{message.content}</div>
          <SuggestedQuestions questions={message.suggestedQuestions} />
        </div>
      );

    default:
      return (
        <div className="bot-message">
          <div className="text">{message.content}</div>
          {message.suggestedQuestions?.length > 0 && (
            <SuggestedQuestions questions={message.suggestedQuestions} />
          )}
        </div>
      );
  }
}

// Helper function
function formatDate(dateString) {
  const date = new Date(dateString);
  return date.toLocaleDateString('vi-VN', { 
    day: '2-digit', 
    month: '2-digit', 
    year: 'numeric' 
  });
}

function formatPrice(price) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price);
}
```

### 3. Cập Nhật TripList Component

```jsx
// components/chat/TripList.jsx

function TripList({ trips, tripType }) {
  if (!trips || trips.length === 0) return null;

  return (
    <div className={`trip-list trip-list--${tripType}`}>
      {trips.map(trip => (
        <TripCard 
          key={trip.tripId} 
          trip={trip} 
          tripType={tripType}
        />
      ))}
    </div>
  );
}
```

### 4. Cập Nhật TripCard Component (Optional)

```jsx
// components/chat/TripCard.jsx

function TripCard({ trip, tripType }) {
  const handleBooking = () => {
    // Navigate to booking page với indicator là chuyến đi hay về
    const params = new URLSearchParams({
      tripType: tripType || 'outbound'
    });
    window.location.href = `/booking/${trip.tripId}?${params.toString()}`;
  };

  return (
    <div className={`trip-card trip-card--${tripType}`}>
      {/* Badge hiển thị chiều đi/về */}
      {tripType === 'return' && (
        <span className="trip-badge trip-badge--return">Chuyến về</span>
      )}
      
      <div className="trip-header">
        <h3>{trip.routeName}</h3>
        <span className="bus-type">{trip.busType}</span>
      </div>

      <div className="trip-details">
        <div className="time">
          <span>🕐 {formatTime(trip.departureTime)}</span>
          <span>→</span>
          <span>{formatTime(trip.arrivalTime)}</span>
        </div>

        <div className="price">
          {trip.hasPromotion && trip.discountedPrice ? (
            <>
              <span className="old-price">{formatPrice(trip.price)}</span>
              <span className="new-price">{formatPrice(trip.discountedPrice)} 🎁</span>
            </>
          ) : (
            <span className="price">{formatPrice(trip.price)}</span>
          )}
        </div>

        <div className="seats">
          🪑 Còn {trip.availableSeats} ghế
        </div>

        {trip.rating && (
          <div className="rating">
            ⭐ {trip.rating}/5
          </div>
        )}
      </div>

      <button 
        className="btn-booking"
        onClick={handleBooking}
      >
        Đặt vé ngay
      </button>
    </div>
  );
}

function formatTime(isoDateTime) {
  const date = new Date(isoDateTime);
  return date.toLocaleTimeString('vi-VN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  });
}

function formatPrice(price) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price);
}
```

---

## 🎨 CSS Styling

```css
/* styles/chat.css */

/* Trip sections */
.trip-section {
  margin: 16px 0;
}

.trip-section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Trip list layout */
.trip-list {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

/* Differentiate outbound vs return trips */
.trip-list--return {
  /* Optional: different styling for return trips */
}

/* Trip card */
.trip-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.trip-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* Return trip badge */
.trip-badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 8px;
}

.trip-badge--return {
  background: #e3f2fd;
  color: #1976d2;
}

/* Round trip summary */
.round-trip-summary {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 12px 16px;
  margin: 16px 0;
  border-left: 4px solid #4caf50;
}

.round-trip-summary .total-price {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.round-trip-summary strong {
  color: #4caf50;
}

/* Responsive */
@media (min-width: 768px) {
  .trip-list {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  }
}
```

---

## 🧪 Test Cases

### Test 1: Vé 1 chiều
```
Input: "Tìm xe Đà Nẵng đi Đà Lạt ngày 10/11"

Expected:
✅ Hiển thị section "📤 Chiều đi"
✅ Hiển thị danh sách trips
❌ KHÔNG hiển thị section "📥 Chiều về"
❌ KHÔNG hiển thị "Tổng giá khứ hồi"
```

### Test 2: Vé khứ hồi
```
Input: "Tìm vé khứ hồi Đà Nẵng đi Đà Lạt ngày 10/11 về ngày 15/11"

Expected:
✅ Hiển thị section "📤 Chiều đi (10/11/2025)"
✅ Hiển thị danh sách trips chiều đi
✅ Hiển thị section "📥 Chiều về (15/11/2025)"
✅ Hiển thị danh sách returnTrips chiều về
✅ Hiển thị "💵 Tổng giá khứ hồi (ước tính)"
```

### Test 3: Vé khứ hồi nhưng không có chuyến về
```
Input: "Vé khứ hồi Đà Nẵng Đà Lạt ngày 10/11 về ngày 31/12"

Expected:
✅ Hiển thị section "📤 Chiều đi"
✅ Hiển thị danh sách trips chiều đi
⚠️ Hiển thị thông báo "Không tìm thấy chuyến về phù hợp"
❌ KHÔNG hiển thị tổng giá khứ hồi
```

---

## 📝 Checklist Implementation

- [x] Backend: Thêm `returnTrips` và `returnTotalResults` vào `AIResponseDTO`
- [x] Backend: Cập nhật `SmartChatBotService.handleSearchTrip()` để trả về returnTrips
- [x] Backend: Cập nhật `SmartChatBotService.handleBookTicket()` để trả về returnTrips
- [ ] Frontend: Cập nhật TypeScript interfaces
- [ ] Frontend: Cập nhật `MessageBubble` component
- [ ] Frontend: Hiển thị section "Chiều đi"
- [ ] Frontend: Hiển thị section "Chiều về" (conditional)
- [ ] Frontend: Hiển thị tổng giá khứ hồi
- [ ] Frontend: Thêm CSS styling
- [ ] Testing: Test với vé 1 chiều
- [ ] Testing: Test với vé khứ hồi
- [ ] Testing: Test edge cases

---

## 🚀 Migration Notes

1. **Breaking Changes**: KHÔNG có. API mới backward compatible vì:
   - `returnTrips` và `returnTotalResults` là optional (có thể null)
   - Frontend cũ sẽ bỏ qua các field mới
   - Frontend mới sẽ hiển thị đầy đủ

2. **Database**: KHÔNG cần migration

3. **Deployment**: 
   - Deploy backend trước
   - Deploy frontend sau
   - Hoặc deploy đồng thời (vì backward compatible)

---

## 📞 Support

Nếu có vấn đề, vui lòng:
1. Kiểm tra backend logs: `logs/spring.log`
2. Kiểm tra browser console
3. Test API trực tiếp bằng Postman/curl

---

**Happy Coding! 🎉**

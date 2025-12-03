# Conversation Context - AI Chat Memory Feature

## Tính năng mới
AI chatbot giờ đây có khả năng **nhớ ngữ cảnh hội thoại** (conversation context), giúp người dùng không cần nhập đầy đủ thông tin trong mỗi tin nhắn.

## Cách hoạt động

### Trước đây:
- **User**: "tìm chuyến đi Nha Trang"
- **AI**: "Bạn muốn đi từ đâu?"
- **User**: "Đà Lạt"
- **AI**: ❌ Không hiểu, chỉ thấy "Đà Lạt" mà không biết điểm đến

### Bây giờ (với Context):
- **User**: "tìm chuyến đi Nha Trang"
- **AI**: "Bạn muốn đi từ đâu?" (Lưu: destination = "Nha Trang")
- **User**: "Đà Lạt"
- **AI**: ✅ Hiểu ngay: "Đà Lạt đi Nha Trang", tìm kiếm và trả về kết quả

### Ví dụ hội thoại liên tiếp:
1. User: "tìm xe đi Đà Nẵng"
   - AI lưu: destination = "Đà Nẵng"
   
2. User: "từ Hà Nội"
   - AI merge: departure = "Hà Nội", destination = "Đà Nẵng"
   
3. User: "ngày mai"
   - AI merge: departure = "Hà Nội", destination = "Đà Nẵng", departureDate = "2025-12-04"
   
4. User: "2 vé"
   - AI merge: tất cả thông tin trên + numberOfTickets = 2
   - AI tìm kiếm và trả về kết quả phù hợp

## Cấu trúc Database

### Bảng `conversation_contexts`
```sql
- id: BIGINT (Primary Key)
- user_email: VARCHAR(255) - Email người dùng
- session_id: VARCHAR(255) - ID phiên chat
- current_intent: VARCHAR(50) - Loại ý định (SEARCH_TRIP, ASK_PRICE, etc.)
- departure: VARCHAR(255) - Điểm đi
- destination: VARCHAR(255) - Điểm đến
- departure_date: DATE - Ngày đi
- number_of_tickets: INT - Số lượng vé
- bus_type: VARCHAR(100) - Loại xe
- price_min: DOUBLE - Giá tối thiểu
- price_max: DOUBLE - Giá tối đa
- last_user_message: TEXT - Tin nhắn cuối cùng
- created_at: TIMESTAMP - Thời gian tạo
- updated_at: TIMESTAMP - Thời gian cập nhật
- expires_at: TIMESTAMP - Thời gian hết hạn (30 phút)
```

## API Endpoints

### 1. Gửi tin nhắn smart chat (có context)
```http
POST /api/ai-chat/smart/send
Content-Type: application/json
Authorization: Bearer {token}

{
  "content": "đến Đà Lạt",
  "sender": "user@example.com"
}
```

### 2. Reset context (bắt đầu hội thoại mới)
```http
POST /api/ai-chat/smart/reset-context
Authorization: Bearer {token}
```

## Luồng xử lý

1. **User gửi tin nhắn** → Frontend gọi API `/api/ai-chat/smart/send`

2. **Backend nhận tin nhắn**:
   - Gọi `IntentExtractionService.extractSearchIntent()` để trích xuất thông tin từ tin nhắn hiện tại
   - Gọi `ConversationContextService.mergeContextWithIntent()` để merge với context từ tin nhắn trước
   - Cập nhật context mới vào database
   - Xử lý theo intent type (SEARCH_TRIP, ASK_PRICE, etc.)

3. **AI trả về kết quả** với thông tin đầy đủ từ context

## Tính năng tự động

### Auto-cleanup
- Context tự động hết hạn sau **30 phút** không hoạt động
- Scheduled job chạy mỗi giờ để xóa các context đã hết hạn

### Auto-merge
- Thông tin mới luôn ghi đè thông tin cũ
- Thông tin cũ được giữ lại nếu tin nhắn mới không cung cấp

## Files được thêm/sửa

### Thêm mới:
1. `ConversationContext.java` - Entity lưu trạng thái hội thoại
2. `ConversationContextRepository.java` - Repository cho context
3. `ConversationContextService.java` - Service quản lý context
4. `V7__create_conversation_contexts_table.sql` - Migration tạo bảng
5. `conversation-context-test.http` - File test API

### Sửa đổi:
1. `SmartChatBotService.java` - Thêm logic merge context
2. `ChatAIController.java` - Thêm endpoint reset context

## Testing

Chạy các test case trong file `testAPI/conversation-context-test.http`:

1. ✅ Test tin nhắn liên tiếp (điểm đi → điểm đến → ngày đi → số vé)
2. ✅ Test reset context và bắt đầu hội thoại mới
3. ✅ Test hỏi giá, lịch trình với context
4. ✅ Test thay đổi loại xe

## Lợi ích

✅ Trải nghiệm chat tự nhiên hơn, giống nói chuyện với người thật  
✅ Không cần nhập lại thông tin đã cung cấp  
✅ AI hiểu được ngữ cảnh và ý định của người dùng  
✅ Tăng tỷ lệ chuyển đổi đặt vé  

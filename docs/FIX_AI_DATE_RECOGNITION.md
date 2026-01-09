# 🐛 Fix AI Chatbot Date Recognition (Sửa lỗi nhận diện ngày tháng)

## ❓ Vấn đề

AI chatbot trả về **ngày sai** khi user hỏi "ngày mai":
- User: "Đà Nẵng đi Vinh ngày mai"
- AI trả về: `departureDate: "2024-05-18"` ❌
- Expected: `departureDate: "2026-01-10"` ✅ (ngày mai thực tế)

---

## 🔍 Nguyên nhân

AI model **KHÔNG BIẾT** ngày hiện tại là ngày nào!

### Prompt cũ (SAI):
```java
private String createIntentExtractionPrompt() {
    return """
        Bạn là trợ lý trích xuất thông tin...
        
        - departureDate: Ngày đi (format: yyyy-MM-dd)
        
        // ❌ KHÔNG CÓ thông tin ngày hiện tại
        """;
}
```

**Kết quả**: AI đoán mò → trả về ngày random như `2024-05-18`

---

## ✅ GIẢI PHÁP: Thêm ngày hiện tại vào prompt

### Code đã sửa:

```java
private String createIntentExtractionPrompt() {
    // ✅ THÊM thông tin ngày hiện tại
    LocalDate today = LocalDate.now();           // 2026-01-09
    LocalDate tomorrow = today.plusDays(1);      // 2026-01-10
    LocalDate dayAfterTomorrow = today.plusDays(2); // 2026-01-11
    
    return String.format("""
        Bạn là trợ lý trích xuất thông tin đặt vé xe...
        
        ✅ THÔNG TIN NGÀY GIỜ HIỆN TẠI:
        - Hôm nay: %s (Thứ %d)
        - Ngày mai: %s (Thứ %d)
        - Ngày kia: %s (Thứ %d)
        - Khi khách hàng nói "hôm nay" -> departureDate = "%s"
        - Khi khách hàng nói "ngày mai" hoặc "mai" -> departureDate = "%s"
        - Khi khách hàng nói "ngày kia" -> departureDate = "%s"
        
        Nhiệm vụ: Phân tích câu chat...
        """,
        today, today.getDayOfWeek().getValue(),
        tomorrow, tomorrow.getDayOfWeek().getValue(),
        dayAfterTomorrow, dayAfterTomorrow.getDayOfWeek().getValue(),
        today, tomorrow, dayAfterTomorrow
    );
}
```

### Prompt thực tế gửi cho AI (hôm nay 9/1/2026):

```
Bạn là trợ lý trích xuất thông tin đặt vé xe...

THÔNG TIN NGÀY GIỜ HIỆN TẠI:
- Hôm nay: 2026-01-09 (Thứ 5)
- Ngày mai: 2026-01-10 (Thứ 6)
- Ngày kia: 2026-01-11 (Thứ 7)
- Khi khách hàng nói "hôm nay" -> departureDate = "2026-01-09"
- Khi khách hàng nói "ngày mai" hoặc "mai" -> departureDate = "2026-01-10"
- Khi khách hàng nói "ngày kia" -> departureDate = "2026-01-11"

Nhiệm vụ: Phân tích câu chat...
```

---

## 🎯 Kết quả sau khi sửa

### Test case 1:
```
User: "Đà Nẵng đi Vinh ngày mai"
AI Response:
{
  "intentType": "SEARCH_TRIP",
  "departure": "Đà Nẵng",
  "destination": "Vinh",
  "departureDate": "2026-01-10" ✅ ĐÚNG!
}
```

### Test case 2:
```
User: "tìm xe hôm nay"
AI Response:
{
  "intentType": "SEARCH_TRIP",
  "departureDate": "2026-01-09" ✅ ĐÚNG!
}
```

### Test case 3:
```
User: "ngày kia đi Đà Lạt"
AI Response:
{
  "intentType": "SEARCH_TRIP",
  "destination": "Đà Lạt",
  "departureDate": "2026-01-11" ✅ ĐÚNG!
}
```

---

## 📝 Files đã sửa

### 1. `IntentExtractionService.java`

**Phương thức được sửa:**
- `createIntentExtractionPrompt()` - Thêm thông tin ngày hiện tại
- `createIntentExtractionPromptWithContext()` - Thêm thông tin ngày hiện tại (cho chat có history)

**Thay đổi:**
```diff
- return """
-     Bạn là trợ lý...
-     """;

+ LocalDate today = LocalDate.now();
+ LocalDate tomorrow = today.plusDays(1);
+ LocalDate dayAfterTomorrow = today.plusDays(2);
+ 
+ return String.format("""
+     THÔNG TIN NGÀY GIỜ HIỆN TẠI:
+     - Hôm nay: %s (Thứ %d)
+     - Ngày mai: %s (Thứ %d)
+     ...
+     """,
+     today, today.getDayOfWeek().getValue(),
+     tomorrow, tomorrow.getDayOfWeek().getValue(),
+     ...
+ );
```

---

## 🧪 Testing

### Trước khi sửa:
```bash
# Test với ngày mai
curl -X POST http://localhost:8080/api/ai-chat/smart/send \
  -H "Content-Type: application/json" \
  -d '{"content":"Đà Nẵng đi Vinh ngày mai","sender":"test@test.com"}'

# Response (SAI):
{
  "departureDate": "2024-05-18" ❌
}
```

### Sau khi sửa:
```bash
# Test lại
curl -X POST http://localhost:8080/api/ai-chat/smart/send \
  -H "Content-Type: application/json" \
  -d '{"content":"Đà Nẵng đi Vinh ngày mai","sender":"test@test.com"}'

# Response (ĐÚNG):
{
  "departureDate": "2026-01-10" ✅
}
```

---

## 💡 Bài học

### ⚠️ Khi làm việc với AI:

1. **AI không biết thời gian thực** → Phải cung cấp trong prompt
2. **Context is king** → Càng nhiều thông tin càng tốt
3. **Test với ngày giờ thực** → Không dùng hardcode date

### ✅ Best practices:

```java
// ✅ ĐÚNG: Dynamic date
LocalDate today = LocalDate.now();
String prompt = String.format("Hôm nay: %s", today);

// ❌ SAI: Hardcode date
String prompt = "Hôm nay: 2026-01-09"; // Sẽ sai vào ngày mai!
```

---

## 📚 Tham khảo

- [Java LocalDate Documentation](https://docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html)
- [String.format() Guide](https://www.baeldung.com/string/format)
- [AI Prompt Engineering Best Practices](https://www.promptingguide.ai/)

---

## ✅ Checklist

- [x] Thêm thông tin ngày hiện tại vào prompt
- [x] Format động với `String.format()`
- [x] Sửa cả 2 methods: `createIntentExtractionPrompt()` và `createIntentExtractionPromptWithContext()`
- [x] Test với "hôm nay", "ngày mai", "ngày kia"
- [x] Verify không có compile errors

---

**Status**: ✅ FIXED  
**Date**: 2026-01-09  
**By**: AI Assistant

# Hướng dẫn tích hợp API Đặt Vé Khứ Hồi cho Frontend

## 1. Định dạng dữ liệu gửi lên API

- **API endpoint:**
  - `POST /api/trips/filter/round-trip?page={page}&size={size}`
- **Content-Type:** `application/json`
- **Body mẫu:**
```json
{
  "startLocation": 1,
  "endLocation": 2,
  "departureDate": "2025-12-06T00:00:00",
  "isRoundTrip": true,
  "returnDate": "2025-12-08T00:00:00",
  "availableSeats": 1
}
```

### Lưu ý về định dạng ngày
- **Luôn gửi ngày ở định dạng:**
  - `YYYY-MM-DDTHH:mm:ss` (không có ký tự `Z` hoặc offset timezone)
  - Ví dụ: `"2025-12-06T00:00:00"`
- **Không gửi ngày ở dạng UTC hoặc có ký tự `Z`**
  - Sai: `"2025-12-05T17:00:00.000Z"`

## 2. Cách lấy và format ngày ở frontend

### Với JavaScript (Date, dayjs, moment...)
```js
// Với dayjs
const departureDate = dayjs(selectedDepartureDate).format('YYYY-MM-DDTHH:mm:ss');
const returnDate = dayjs(selectedReturnDate).format('YYYY-MM-DDTHH:mm:ss');

// Với Date JS
function formatDate(date) {
  return date.toISOString().slice(0, 19); // Lấy đến giây, bỏ 'Z'
}
```

### Đảm bảo ngày gửi lên là giờ Việt Nam (Asia/Ho_Chi_Minh)
- Nếu dùng date picker, nên lấy giá trị local và format như trên.
- Nếu cần truyền timezone, thêm trường `timeZone` vào body:
```json
{
  ...,
  "timeZone": "Asia/Ho_Chi_Minh"
}
```

## 3. Xử lý trường hợp khứ hồi
- Nếu người dùng chọn khứ hồi (`isRoundTrip: true`), **bắt buộc** phải truyền `returnDate`.
- Nếu không chọn khứ hồi (`isRoundTrip: false` hoặc không truyền), chỉ cần truyền thông tin chiều đi.

## 4. Xử lý response
- API trả về danh sách chuyến đi chiều đi và chiều về (nếu là khứ hồi).
- Nếu không có chuyến phù hợp, API trả về thông báo lỗi.

## 5. Ví dụ tích hợp với fetch
```js
fetch('http://localhost:8080/api/trips/filter/round-trip?page=0&size=10', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    startLocation: 1,
    endLocation: 2,
    departureDate: dayjs(selectedDepartureDate).format('YYYY-MM-DDTHH:mm:ss'),
    isRoundTrip: true,
    returnDate: dayjs(selectedReturnDate).format('YYYY-MM-DDTHH:mm:ss'),
    availableSeats: 1
  })
})
.then(res => res.json())
.then(data => {
  console.log('Kết quả:', data);
});
```

## 6. Kiểm tra lỗi
- Nếu ngày về trước ngày đi, hoặc thiếu ngày về khi chọn khứ hồi, API sẽ trả về lỗi.
- Luôn kiểm tra response và hiển thị thông báo phù hợp cho người dùng.

---
**Tóm tắt:**
- Luôn gửi ngày ở định dạng `YYYY-MM-DDTHH:mm:ss` (không có `Z`)
- Truyền đủ thông tin khi chọn khứ hồi
- Xử lý response và lỗi rõ ràng

Nếu cần ví dụ chi tiết hơn, hãy liên hệ backend!

# Hướng Dẫn API Thanh Toán Khứ Hồi (Round-Trip Payment)

## Tổng Quan

Hệ thống đã được nâng cấp để hỗ trợ thanh toán khứ hồi (round-trip) với một link thanh toán duy nhất cho cả hai chiều đi và về.

## Cách Hoạt Động

### 1. Đặt Vé Khứ Hồi (Frontend)

**Bước 1:** Gọi API tạo booking cho **chiều đi**:
```http
POST /api/bookings
Content-Type: application/json

{
    "tripId": 1,
    "seatNumber": "A1,A2",
    "totalAmount": 500000,
    "guestFullName": "Nguyen Van A",
    "guestEmail": "nguyenvana@email.com",
    "guestPhone": "0123456789"
}
```
Response:
```json
{
    "code": 200,
    "message": "Thêm đặt vé thành công",
    "result": {
        "bookingId": 101,
        "bookingCode": "BK123456"
    }
}
```

**Bước 2:** Gọi API tạo booking cho **chiều về**:
```http
POST /api/bookings
Content-Type: application/json

{
    "tripId": 2,
    "seatNumber": "B1,B2",
    "totalAmount": 500000,
    "guestFullName": "Nguyen Van A",
    "guestEmail": "nguyenvana@email.com",
    "guestPhone": "0123456789"
}
```
Response:
```json
{
    "code": 200,
    "message": "Thêm đặt vé thành công",
    "result": {
        "bookingId": 102,
        "bookingCode": "BK789012"
    }
}
```

### 2. Tạo Payment (Một Link Thanh Toán)

**Cách mới:** Gửi danh sách `bookingIds` để thanh toán cả hai chiều:
```http
POST /api/payments/create
Content-Type: application/json

{
    "bookingIds": [101, 102],
    "paymentMethod": "ZALOPAY"
}
```

**Cách cũ (backward compatible):** Vẫn hỗ trợ thanh toán 1 booking:
```http
POST /api/payments/create
Content-Type: application/json

{
    "bookingId": 101,
    "paymentMethod": "ZALOPAY"
}
```

**Response:**
```json
{
    "code": 200,
    "message": "Payment created successfully",
    "result": {
        "paymentId": 1,
        "status": "pending",
        "paymentUrl": "https://sandbox.zalopay.vn/...",
        "bookingId": 101,
        "bookingIds": [101, 102]
    }
}
```

### 3. Sau Khi Thanh Toán Thành Công

Hệ thống sẽ tự động:
- Cập nhật trạng thái của **tất cả** booking thành `confirmed`
- Tạo ticket cho **tất cả** booking
- Gửi email xác nhận cho **từng** booking

## Cấu Trúc Dữ Liệu

### Bảng Payments

| Cột | Kiểu | Mô Tả |
|-----|------|-------|
| payment_id | BIGINT | ID payment |
| booking_id | BIGINT (nullable) | ID booking đầu tiên (backward compatible) |
| booking_ids | VARCHAR(500) | Danh sách booking IDs, phân cách bằng dấu phẩy (VD: "101,102") |
| amount | DECIMAL | Tổng tiền của tất cả bookings |
| ... | ... | ... |

### Quy Tắc Lưu Trữ

- **Đặt 1 chiều (1 booking):** 
  - `booking_id = 101`
  - `booking_ids = NULL`

- **Đặt khứ hồi (2+ bookings):**
  - `booking_id = 101` (booking đầu tiên)
  - `booking_ids = "101,102"`

## Migration SQL

File migration: `V4__add_booking_ids_to_payments.sql`

```sql
-- Thêm cột booking_ids
ALTER TABLE payments ADD COLUMN IF NOT EXISTS booking_ids VARCHAR(500) NULL;

-- Cho phép booking_id nullable
ALTER TABLE payments ALTER COLUMN booking_id DROP NOT NULL;

-- Index cho tìm kiếm
CREATE INDEX IF NOT EXISTS idx_payments_booking_ids ON payments(booking_ids);
```

## Lưu Ý Cho Frontend
1. **Khi đặt khứ hồi:**
    - Lưu cả hai `bookingId` từ response của API booking (chiều đi và chiều về).
    - Khi tạo payment, gửi mảng `bookingIds` và chọn `paymentMethod` là `ZALOPAY`.

2. **Redirect URL:**
    - Sau khi thanh toán, user sẽ được redirect về URL với `paymentId`. Frontend có thể dùng `paymentId` hoặc `bookingIds` để lấy thông tin chi tiết các booking.

3. **Hiển thị chi tiết:**
    - Giá tiền từng chiều: lấy từ từng `booking.totalAmount`.
    - Tổng tiền đã thanh toán: lấy từ `payment.amount`.
    - Nếu là khứ hồi, duyệt qua danh sách `bookingIds` để lấy thông tin từng chiều.

## Ví Dụ Code Frontend (JavaScript)
```javascript
// 1. Tạo booking chiều đi
const outboundBooking = await fetch('/api/bookings', {
    method: 'POST',
    body: JSON.stringify({
        tripId: outboundTripId,
        seatNumber: "A1,A2",
        totalAmount: 500000,
        // ... các thông tin khác
    })
}).then(res => res.json());

// 2. Tạo booking chiều về
const returnBooking = await fetch('/api/bookings', {
    method: 'POST',
    body: JSON.stringify({
        tripId: returnTripId,
        seatNumber: "B1,B2",
        totalAmount: 500000,
        // ... các thông tin khác
    })
}).then(res => res.json());

// 3. Tạo payment với cả hai booking (ZaloPay)
const payment = await fetch('/api/payments/create', {
    method: 'POST',
    body: JSON.stringify({
        bookingIds: [
            outboundBooking.result.bookingId,
            returnBooking.result.bookingId
        ],
        paymentMethod: "ZALOPAY"
    })
}).then(res => res.json());

// 4. Redirect đến trang thanh toán ZaloPay
window.location.href = payment.result.paymentUrl;
```

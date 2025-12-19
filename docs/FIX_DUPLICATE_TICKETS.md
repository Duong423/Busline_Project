# Fix: Lỗi Tạo Duplicate Tickets Với Giá Chia Đôi

## Mô tả lỗi
Khi đặt chuyến từ **Bến xe trung tâm Đà Nẵng → Bến xe Đà Lạt**, hệ thống tạo ra **hai vé (tickets) khác nhau** nhưng có:
- **Cùng số ghế**
- **Giá mỗi vé = giá gốc chia đôi**

## Nguyên nhân

### 1. Hàm `createTicketsFromBooking` bị gọi nhiều lần
Trong `PaymentController`, logic xử lý callback từ các payment gateway (PayPal, VNPay, ZaloPay) có thể tạo duplicate tickets khi:
- `bookingIds` list chứa cùng một booking ID nhiều lần
- Hoặc callback bị xử lý nhiều lần do network retry

### 2. Không có cơ chế kiểm tra tickets đã tồn tại
Trước đây, hàm `createTicketsFromBooking` không kiểm tra xem booking đã có tickets chưa, nên mỗi lần gọi đều tạo mới.

### 3. Duplicate seat numbers trong booking
Nếu `booking.getSeatNumber()` chứa seats trùng lặp (ví dụ: "A1,A1"), hệ thống sẽ tạo 2 vé cho cùng một ghế.

## Giải pháp

### 1. Thêm method `findByBookingId` vào TicketRepository
```java
// Tìm tất cả tickets theo booking ID
@Query("SELECT t FROM Tickets t WHERE t.booking.id = :bookingId")
List<Tickets> findByBookingId(@Param("bookingId") Long bookingId);
```

### 2. Kiểm tra tickets đã tồn tại trong `createTicketsFromBooking`
```java
// Kiểm tra xem booking này đã có tickets chưa
List<Tickets> existingTickets = ticketRepository.findByBookingId(bookingId);
if (!existingTickets.isEmpty()) {
    System.out.println("WARNING: Tickets already exist for booking ID: " + bookingId);
    // Trả về tickets đã tồn tại thay vì tạo mới
    return existingTickets.stream()
            .map(TicketMapper::toResponseDTO)
            .collect(Collectors.toList());
}
```

### 3. Loại bỏ duplicate seats
```java
// Loại bỏ duplicate seats để tránh tạo vé trùng
List<String> uniqueSeats = java.util.stream.Stream.of(seatNumbers)
        .map(String::trim)
        .distinct()
        .collect(Collectors.toList());
```

## Files đã sửa

1. **TicketRepository.java**
   - Thêm method `findByBookingId(Long bookingId)`

2. **TicketServiceImpl.java**
   - Thêm kiểm tra tickets đã tồn tại trước khi tạo mới
   - Loại bỏ duplicate seat numbers
   - Sử dụng `uniqueSeats` thay vì `seatNumbers` trong toàn bộ logic

## Testing

### Test Case 1: Duplicate Call Protection
```
1. Tạo booking cho chuyến Đà Nẵng → Đà Lạt với 1 ghế (A1)
2. Gọi createTicketsFromBooking(bookingId, null) lần 1 → Tạo 1 ticket
3. Gọi createTicketsFromBooking(bookingId, null) lần 2 → Return ticket cũ, không tạo mới
4. Verify: Chỉ có 1 ticket trong database
```

### Test Case 2: Duplicate Seat Number
```
1. Tạo booking với seatNumber = "A1,A1" (duplicate)
2. Gọi createTicketsFromBooking(bookingId, null)
3. Verify: Chỉ có 1 ticket được tạo cho ghế A1
4. Verify: Giá vé = totalAmount (không bị chia đôi)
```

### Test Case 3: Normal Case
```
1. Tạo booking với seatNumber = "A1,A2" (2 ghế khác nhau)
2. Gọi createTicketsFromBooking(bookingId, null)
3. Verify: 2 tickets được tạo
4. Verify: Giá mỗi vé = totalAmount / 2
```

## Logs để debug

Khi chạy, check các log sau:
```
DEBUG: Total amount: 500000, Seats: 2, Price per seat: 250000
DEBUG: Saving 2 tickets to the database
WARNING: Found duplicate seats in booking. Original: 3, Unique: 2
WARNING: Tickets already exist for booking ID: 123. Skipping ticket creation.
```

## Khuyến nghị thêm

### 1. Thêm unique constraint ở database level
```sql
ALTER TABLE tickets 
ADD CONSTRAINT unique_booking_seat 
UNIQUE (booking_id, seat_number);
```

### 2. Thêm transaction isolation
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public List<TicketResponseDTO> createTicketsFromBooking(Long bookingId, SellMethod sellMethod)
```

### 3. Monitor duplicate calls
Thêm metric để track số lần `createTicketsFromBooking` bị gọi với cùng booking ID.

## Lưu ý
- Fix này chỉ ngăn chặn tạo duplicate tickets mới
- Với tickets đã bị duplicate trong database, cần chạy script cleanup riêng
- Cần test kỹ các flows: PayPal, VNPay, ZaloPay callback

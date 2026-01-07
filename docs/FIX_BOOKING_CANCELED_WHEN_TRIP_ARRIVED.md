# 🐛 Fix: Booking Bị Cancel Khi Driver Set Trip Thành Arrived

> **Ngày sửa**: 7/1/2026  
> **Mức độ**: 🔴 CRITICAL BUG  
> **Ảnh hưởng**: Booking của khách hàng bị tự động cancel khi tài xế hoàn thành chuyến đi

---

## 📋 Mô Tả Vấn Đề

### Hiện Tượng

Khi tài xế set status của trip thành `arrived` (đã đến nơi) trong app tài xế, **một số booking của khách hàng bị tự động chuyển sang status `canceled_by_operator`** thay vì `completed`.

### Kịch Bản Tái Hiện

1. Khách hàng đặt vé và thanh toán thành công → Booking status = `confirmed`
2. Tài xế bắt đầu chuyến đi → Trip status = `departed`
3. Tài xế kết thúc chuyến đi → Trip status = `arrived`
4. **Hệ thống gọi API auto-complete bookings** → Booking *nên* chuyển thành `completed`
5. ❌ **NHƯNG** một số booking lại bị chuyển thành `canceled_by_operator`

### Tại Sao Lại Xảy Ra?

Hệ thống có **2 luồng xử lý song song** gây conflict:

#### Luồng 1: Auto-Complete Bookings (ĐÚNG) ✅
```java
// File: TripServiceImpl.java - updateTripStatus()
if (request.getStatus() == TripStatus.arrived) {
    completedBookings = bookingService.markBookingsAsCompletedWhenTripArrived(tripId);
    // ✅ Chuyển booking từ "confirmed" → "completed"
}
```

#### Luồng 2: Scheduler Auto-Cancel Expired Bookings (SAI) ❌
```java
// File: SeatReleaseService.java
@Scheduled(fixedRate = 5 * 60 * 1000) // Chạy mỗi 5 phút
public void periodicExpiredSeatsCheck() {
    List<Bookings> expiredBookings = bookingRepository.findExpiredPendingBookings(...);
    // ❌ Cancel tất cả booking quá 15 phút, KHÔNG CHECK TRIP STATUS
    for (Bookings booking : expiredBookings) {
        booking.setStatus(BookingStatus.canceled_by_operator);
    }
}
```

#### Query Lỗi (CŨ)
```java
// BookingRepository.java
@Query("SELECT b FROM Bookings b WHERE " +
    "b.createdAt < :cutoffTime AND " +
    "(b.payment IS NULL OR b.payment.status = 'pending') AND " +
    "b.status NOT IN ('canceled_by_customer', 'canceled_by_operator', 'completed')")
List<Bookings> findExpiredPendingBookings(@Param("cutoffTime") Instant cutoffTime);
```

**Vấn đề**: Query này tìm tất cả booking có:
- Được tạo > 15 phút trước
- Payment = null hoặc pending
- Status không phải canceled/completed
- **NHƯNG KHÔNG CHECK TRIP STATUS!**

→ Booking của trip đã `arrived`/`departed`/`delayed` cũng bị cancel nếu quá 15 phút!

---

## ✅ Giải Pháp

### Thay Đổi Code

**File**: `src/main/java/com/busify/project/booking/repository/BookingRepository.java`

#### Trước (CŨ - LỖI)
```java
@Query("SELECT b FROM Bookings b WHERE " +
    "b.createdAt < :cutoffTime AND " +
    "(b.payment IS NULL OR b.payment.status = 'pending') AND " +
    "b.status NOT IN ('canceled_by_customer', 'canceled_by_operator', 'completed')")
List<Bookings> findExpiredPendingBookings(@Param("cutoffTime") Instant cutoffTime);
```

#### Sau (MỚI - FIX) ✅
```java
// QUAN TRỌNG: Chỉ cancel booking của trip chưa khởi hành (status = scheduled hoặc on_sell)
// Không cancel booking của trip đã departed/delayed/arrived/cancelled
@Query("SELECT b FROM Bookings b WHERE " +
    "b.createdAt < :cutoffTime AND " +
    "(b.payment IS NULL OR b.payment.status = 'pending') AND " +
    "b.status NOT IN ('canceled_by_customer', 'canceled_by_operator', 'completed') AND " +
    "b.trip.status IN ('scheduled', 'on_sell')")
List<Bookings> findExpiredPendingBookings(@Param("cutoffTime") Instant cutoffTime);
```

**Điều kiện mới**: `b.trip.status IN ('scheduled', 'on_sell')`

→ **CHỈ cancel booking của trip chưa khởi hành**  
→ **KHÔNG cancel booking của trip đã departed/delayed/arrived/cancelled**

---

## 📊 Luồng Xử Lý Sau Khi Fix

### Trường Hợp 1: Booking Bình Thường (Có Payment)

```
1. Khách tạo booking → status = "pending_payment"
2. Khách thanh toán trong 15 phút → payment.status = "paid"
                                   → booking.status = "confirmed"
3. Tài xế khởi hành → trip.status = "departed"
4. Tài xế đến nơi → trip.status = "arrived"
5. ✅ Auto-complete: booking.status = "completed"
```

**Scheduler KHÔNG CAN THIỆP** vì:
- Payment đã paid (không phải pending)
- Trip đã departed/arrived (không phải scheduled/on_sell)

### Trường Hợp 2: Booking Quá Hạn Chưa Thanh Toán

```
1. Khách tạo booking → status = "pending_payment"
2. Khách KHÔNG thanh toán
3. ⏰ Sau 15 phút:
   - Trip vẫn status = "scheduled" hoặc "on_sell"
   - ✅ Scheduler cancel booking → status = "canceled_by_operator"
   - ✅ Release ghế về available
```

### Trường Hợp 3: Trip Đã Khởi Hành (Booking Chưa Thanh Toán)

```
1. Khách tạo booking → status = "pending_payment"
2. Khách KHÔNG thanh toán
3. Tài xế khởi hành → trip.status = "departed"
4. ⏰ Sau 15 phút:
   - Trip đã status = "departed" 
   - ❌ Scheduler KHÔNG cancel (vì trip không phải scheduled/on_sell)
   - Booking vẫn "pending_payment"
```

⚠️ **Lưu ý**: Trường hợp 3 cần xử lý riêng bằng logic khác (ví dụ: auto-cancel khi trip departed).

---

## 🧪 Test Cases

### Test 1: Booking Của Trip Arrived Không Bị Cancel

**Setup**:
```sql
INSERT INTO trips (id, status, departure_time, ...) 
VALUES (100, 'arrived', NOW() - INTERVAL 2 HOUR, ...);

INSERT INTO bookings (id, trip_id, status, created_at, ...) 
VALUES (1000, 100, 'confirmed', NOW() - INTERVAL 20 MINUTE, ...);
```

**Expected**:
- Sau khi scheduler chạy: Booking 1000 vẫn `confirmed` (hoặc `completed` nếu auto-complete đã chạy)
- **KHÔNG** chuyển thành `canceled_by_operator`

### Test 2: Booking Quá Hạn Của Trip Chưa Khởi Hành Bị Cancel

**Setup**:
```sql
INSERT INTO trips (id, status, departure_time, ...) 
VALUES (101, 'scheduled', NOW() + INTERVAL 1 HOUR, ...);

INSERT INTO bookings (id, trip_id, status, created_at, payment_id, ...) 
VALUES (1001, 101, 'pending_payment', NOW() - INTERVAL 20 MINUTE, NULL, ...);
```

**Expected**:
- Sau khi scheduler chạy: Booking 1001 chuyển thành `canceled_by_operator`
- Ghế được release về `available`

### Test 3: Booking Của Trip Departed Không Bị Cancel

**Setup**:
```sql
INSERT INTO trips (id, status, departure_time, ...) 
VALUES (102, 'departed', NOW() - INTERVAL 30 MINUTE, ...);

INSERT INTO bookings (id, trip_id, status, created_at, ...) 
VALUES (1002, 102, 'confirmed', NOW() - INTERVAL 1 HOUR, ...);
```

**Expected**:
- Sau khi scheduler chạy: Booking 1002 vẫn `confirmed`
- **KHÔNG** bị cancel

---

## 📝 Các File Liên Quan

| File | Thay Đổi | Lý Do |
|------|----------|-------|
| `BookingRepository.java` | ✅ Sửa query `findExpiredPendingBookings` | Thêm điều kiện check trip.status |
| `SeatReleaseService.java` | ❌ Không thay đổi | Logic vẫn đúng, chỉ query sai |
| `TripServiceImpl.java` | ❌ Không thay đổi | Logic auto-complete đã đúng |

---

## ⚠️ Breaking Changes

**KHÔNG CÓ BREAKING CHANGES**

- API không thay đổi
- Database schema không thay đổi
- Chỉ sửa logic internal của scheduler

---

## 🚀 Deployment

### Kiểm Tra Trước Khi Deploy

```bash
# 1. Build project
mvn clean install

# 2. Chạy test
mvn test

# 3. Kiểm tra query mới
# Query này NÊN trả về 0 kết quả nếu không có booking quá hạn
```

```sql
SELECT b.* 
FROM bookings b
JOIN trips t ON b.trip_id = t.id
WHERE b.created_at < NOW() - INTERVAL 15 MINUTE
  AND (b.payment_id IS NULL OR (SELECT status FROM payments p WHERE p.id = b.payment_id) = 'pending')
  AND b.status NOT IN ('canceled_by_customer', 'canceled_by_operator', 'completed')
  AND t.status IN ('scheduled', 'on_sell');
```

### Sau Khi Deploy

1. **Monitor logs** trong 1 giờ đầu:
```bash
tail -f logs/spring.log | grep -E "periodicExpiredSeatsCheck|Cancelled expired booking"
```

2. **Kiểm tra booking của trip arrived**:
```sql
SELECT 
    b.id, 
    b.booking_code, 
    b.status AS booking_status,
    t.id AS trip_id,
    t.status AS trip_status
FROM bookings b
JOIN trips t ON b.trip_id = t.id
WHERE t.status = 'arrived'
  AND b.status = 'canceled_by_operator'
  AND b.updated_at > NOW() - INTERVAL 1 HOUR;
```

→ Kết quả **NÊN LÀ 0 ROWS** (không có booking nào bị cancel khi trip arrived)

3. **Alert nếu phát hiện anomaly**:
- Booking của trip `arrived` bị cancel
- Booking của trip `departed` bị cancel
- Booking của trip `delayed` bị cancel

---

## 📞 Rollback Plan

Nếu phát hiện lỗi sau khi deploy, rollback bằng cách:

```java
// Revert query về version cũ
@Query("SELECT b FROM Bookings b WHERE " +
    "b.createdAt < :cutoffTime AND " +
    "(b.payment IS NULL OR b.payment.status = 'pending') AND " +
    "b.status NOT IN ('canceled_by_customer', 'canceled_by_operator', 'completed')")
List<Bookings> findExpiredPendingBookings(@Param("cutoffTime") Instant cutoffTime);
```

Và rebuild + redeploy.

---

## 🎯 Kết Luận

**Root Cause**: Scheduler auto-cancel booking không check trip status  
**Solution**: Thêm điều kiện `trip.status IN ('scheduled', 'on_sell')` vào query  
**Impact**: CRITICAL - Ảnh hưởng trực tiếp đến trải nghiệm khách hàng  
**Risk**: LOW - Chỉ sửa 1 query, không thay đổi logic nghiệp vụ  

**Benefit**:
- ✅ Booking của trip đã arrived/departed không bị cancel nhầm
- ✅ Khách hàng nhận được status đúng: `completed` thay vì `canceled_by_operator`
- ✅ Scheduler vẫn hoạt động đúng cho booking thật sự quá hạn

---

**Happy Fixing! 🎉**

# Promotion Usage Count Feature

## Tổng quan
Feature này giúp hiển thị số lượng voucher/promotion còn lại và đã sử dụng trên frontend.

## Các trường mới trong `PromotionResponseDTO`

```java
private Integer usageLimit;        // Tổng số voucher có thể sử dụng (không đổi)
private Long usedCount;            // Số lượng voucher đã sử dụng (real-time)
private Long remainingCount;       // Số lượng voucher còn lại (real-time)
```

## Logic tính toán

### 1. **usedCount** - Số lượng đã sử dụng
- Được tính từ database: `SELECT COUNT(*) FROM user_promotion WHERE promotion_id = ? AND is_used = true`
- Trả về số lượng user đã sử dụng promotion này

### 2. **remainingCount** - Số lượng còn lại
- **Nếu có giới hạn** (`usageLimit` > 0):
  ```java
  remainingCount = Math.max(0, usageLimit - usedCount)
  ```
- **Nếu không giới hạn** (`usageLimit` = null hoặc 0):
  ```java
  remainingCount = null  // Nghĩa là unlimited
  ```

## Các endpoint trả về thông tin này

✅ Tất cả các endpoint sau đều trả về đầy đủ `usedCount` và `remainingCount`:

1. `GET /api/promotions/{id}` - Get by ID
2. `GET /api/promotions/code/{code}` - Get by code
3. `GET /api/promotions` - Get all
4. `POST /api/promotions/filter` - Filter with pagination
5. `GET /api/promotions/current` - Get current active promotions
6. `GET /api/promotions/auto` - Get auto promotions
7. `GET /api/promotions/best-auto` - Get best auto promotion
8. `POST /api/promotions/validate` - Validate promotion
9. `POST /api/promotions/validate-by-id` - Validate by ID
10. `GET /api/promotions/auto-with-conditions` - Auto promotions with completed conditions

## Response Example

```json
{
  "id": 1,
  "code": "SUMMER2024",
  "discountType": "PERCENTAGE",
  "promotionType": "coupon",
  "discountValue": 20.00,
  "minOrderValue": 100000.00,
  "startDate": "2024-01-01",
  "endDate": "2024-12-31",
  "usageLimit": 100,
  "usedCount": 35,
  "remainingCount": 65,
  "status": "active",
  "priority": 1,
  "campaignId": null,
  "conditions": []
}
```

## Hiển thị trên Frontend

### 1. Trường hợp có giới hạn
```javascript
if (promotion.usageLimit && promotion.remainingCount !== null) {
  // Hiển thị: "Còn lại: 65/100 voucher"
  // Hoặc: "Đã sử dụng: 35/100"
  // Hoặc progress bar: 35%
}
```

### 2. Trường hợp không giới hạn
```javascript
if (!promotion.usageLimit || promotion.remainingCount === null) {
  // Hiển thị: "Không giới hạn"
  // Hoặc: "Unlimited"
}
```

### 3. Trường hợp hết voucher
```javascript
if (promotion.remainingCount === 0) {
  // Hiển thị: "Đã hết voucher"
  // Disable button claim/apply
  // Màu xám hoặc thêm badge "Hết"
}
```

## Performance

- Query `countUsedByPromotionId` được tối ưu với index trên `promotion_id` và `is_used`
- Kết quả được tính real-time khi gọi API, không cache (đảm bảo tính chính xác)
- Nếu cần cache, có thể implement Redis cache với TTL ngắn (5-10 giây)

## Testing

Sử dụng file `testAPI/promotion-usage-count-test.http` để test các endpoint.

## Notes

⚠️ **Lưu ý quan trọng:**
- `usedCount` và `remainingCount` chỉ được tính khi có `UserPromotionRepository` được truyền vào mapper
- Nếu không truyền repository (old code), 2 trường này sẽ null
- Đảm bảo tất cả service methods đã được update để truyền repository

## Migration Guide

Nếu frontend đã có code cũ, cần update:

**Before:**
```javascript
// Chỉ có usageLimit
promotion.usageLimit // 100
```

**After:**
```javascript
// Có đầy đủ thông tin
promotion.usageLimit      // 100 (tổng)
promotion.usedCount       // 35 (đã dùng)
promotion.remainingCount  // 65 (còn lại)
```

## Future Improvements

1. **Cache với Redis:**
   ```java
   @Cacheable(value = "promotionUsageCount", key = "#promotionId")
   public long getUsageCount(Long promotionId) { ... }
   ```

2. **Real-time update với WebSocket:**
   - Push update khi có người claim/use voucher
   - Hiển thị số lượng còn lại real-time

3. **Warning khi sắp hết:**
   - Backend trả thêm flag `isLowStock` khi remainingCount < 10
   - Frontend hiển thị badge "Sắp hết"

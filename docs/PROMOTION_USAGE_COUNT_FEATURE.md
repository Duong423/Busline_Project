# Feature: Hiển thị Số Lượng Voucher (Usage Count)

## Tổng quan
Đã thêm logic để hiển thị số lượng voucher đã sử dụng và còn lại cho mỗi promotion trên response API.

## Các thay đổi

### 1. PromotionResponseDTO
**File**: `src/main/java/com/busify/project/promotion/dto/response/PromotionResponseDTO.java`

Đã thêm 2 trường mới:
```java
private Long usedCount;        // Số lượng voucher đã sử dụng
private Long remainingCount;   // Số lượng voucher còn lại (null nếu không giới hạn)
```

### 2. PromotionMapper
**File**: `src/main/java/com/busify/project/promotion/mapper/PromotionMapper.java`

- Thêm overload method `convertToDTO(Promotion, UserPromotionRepository)`
- Tự động tính toán `usedCount` và `remainingCount` khi có repository
- Logic tính toán:
  - `usedCount` = Số lượng UserPromotion có `isUsed = true`
  - `remainingCount` = `usageLimit - usedCount` (nếu có usageLimit)
  - `remainingCount` = `null` (nếu không có usageLimit - unlimited)

### 3. PromotionServiceImpl
**File**: `src/main/java/com/busify/project/promotion/service/impl/PromotionServiceImpl.java`

Đã cập nhật TẤT CẢ các method trả về `PromotionResponseDTO` để truyền `userPromotionRepository`:
- `createPromotion()`
- `getPromotionById()`
- `getPromotionByCode()`
- `getAllPromotions()`
- `filterPromotions()`
- `updatePromotion()`
- `findActiveAutoPromotions()`
- `findBestAutoPromotion()`
- `validateAndApplyPromotion()`
- `validateAndApplyPromotionById()`
- `getAllCurrentPromotions()`
- `getAutoPromotionsWithCompletedConditions()`

### 4. PromotionController
**File**: `src/main/java/com/busify/project/promotion/controller/PromotionController.java`

**Quan trọng**: Đã sắp xếp lại thứ tự các endpoint để tránh conflict routing:
- Các endpoint cụ thể (như `/current`, `/filter`, `/code/{code}`) được đặt **TRƯỚC**
- Các endpoint có path variable (như `/{id}`) được đặt **SAU**

**Lý do**: Spring Boot match endpoint theo thứ tự từ trên xuống, nên `/current` phải đặt trước `/{id}` để tránh "current" bị nhầm là một `id`.

**Endpoint mới**:
- `GET /api/promotions/current` - Alias mới, ngắn gọn hơn
- `GET /api/promotions/current-promotions` - Endpoint cũ, giữ lại để backward compatibility

Cả 2 endpoint đều trỏ đến cùng 1 handler method.

## Response Format

```json
{
  "code": 200,
  "result": {
    "id": 1,
    "code": "SUMMER2024",
    "discountType": "PERCENTAGE",
    "promotionType": "coupon",
    "discountValue": 20.00,
    "minOrderValue": 100000.00,
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "usageLimit": 100,           // Tổng số voucher có thể sử dụng
    "usedCount": 35,              // ✨ MỚI: Số voucher đã sử dụng
    "remainingCount": 65,         // ✨ MỚI: Số voucher còn lại
    "status": "active",
    "priority": 1,
    "campaignId": null,
    "conditions": []
  }
}
```

### Trường hợp đặc biệt:
- Nếu `usageLimit = null` (unlimited): `remainingCount = null`
- Nếu `usageLimit = 0`: Được coi là unlimited, `remainingCount = null`
- Nếu `usedCount > usageLimit`: `remainingCount = 0` (không âm)

## Test API

File test: `testAPI/promotion-usage-count-test.http`

### Các endpoint đã test:
1. `GET /api/promotions/{id}` - Lấy promotion theo ID
2. `GET /api/promotions/code/{code}` - Lấy promotion theo code
3. `GET /api/promotions` - Lấy tất cả promotions
4. `GET /api/promotions/filter` - Filter promotions với pagination
5. `GET /api/promotions/current` - Lấy promotions hiện tại (endpoint mới)
6. `GET /api/promotions/current-promotions` - Lấy promotions hiện tại (endpoint cũ)

## Cách sử dụng trên Frontend

### 1. Hiển thị số lượng voucher còn lại
```javascript
const promotion = response.data.result;

if (promotion.remainingCount === null) {
  // Unlimited voucher
  return "Không giới hạn";
} else if (promotion.remainingCount === 0) {
  // Hết voucher
  return "Đã hết";
} else {
  // Còn voucher
  return `Còn ${promotion.remainingCount} voucher`;
}
```

### 2. Hiển thị progress bar
```javascript
const percentage = (promotion.usedCount / promotion.usageLimit) * 100;

// Example with React/Vue component
<ProgressBar 
  value={promotion.usedCount} 
  max={promotion.usageLimit} 
  label={`${promotion.usedCount}/${promotion.usageLimit}`}
/>
```

### 3. Disable button khi hết voucher
```javascript
const isAvailable = promotion.remainingCount === null || promotion.remainingCount > 0;

<Button disabled={!isAvailable}>
  {isAvailable ? "Nhận voucher" : "Đã hết"}
</Button>
```

## Performance Note

- Query `countUsedByPromotionId` được execute cho MỖI promotion trong response
- Nếu có nhiều promotions (>100), có thể ảnh hưởng performance
- Có thể cân nhắc optimize bằng cách:
  - Cache kết quả count
  - Batch query với JOIN
  - Lazy loading cho count (chỉ load khi cần)

## Backward Compatibility

✅ **Hoàn toàn tương thích ngược**:
- Các field cũ không thay đổi
- Frontend cũ vẫn hoạt động bình thường
- Chỉ thêm 2 field mới: `usedCount` và `remainingCount`
- Endpoint cũ `/current-promotions` vẫn hoạt động

## Migration

Không cần migration database vì chỉ thay đổi ở tầng application logic.

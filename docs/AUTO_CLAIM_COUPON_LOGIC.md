# 🎟️ Auto-Claim COUPON Logic - Fix Documentation

## 📋 Vấn đề gặp phải

### Error Log:
```
com.busify.project.booking.exception.BookingPromotionException: 
Promotion code '7VHB9F' is not applicable: Promotion not available for this user
```

### Root Cause:
- Logic cũ yêu cầu user **PHẢI claim COUPON trước** khi sử dụng
- Method `validateAndApplyPromotion()` check `canUsePromotion()` → return `false` nếu user chưa claim
- Repository method `findAvailablePromotionForUser()` chỉ tìm promotions đã claim và chưa sử dụng

### Vấn đề nghiệp vụ:
- User không biết phải claim promotion trước khi dùng
- Nhiều promotion codes được phát hành nhưng user không thể dùng được
- UX không thân thiện - yêu cầu 2 bước: claim → apply

---

## ✅ Giải pháp đã triển khai

### Auto-Claim Mechanism
Khi user apply một COUPON promotion code:

1. **Kiểm tra promotion hợp lệ** (status, date, min order value, usage limit)
2. **Tự động claim cho user** nếu:
   - User chưa claim promotion này
   - Promotion chưa đạt usage limit
3. **Validate và apply** promotion vào booking

### Code Changes

#### 1. Added new method: `autoClaimCouponIfNeeded()`
```java
/**
 * Tự động claim COUPON cho user nếu chưa claim
 * - Chỉ áp dụng cho promotion type = COUPON
 * - Kiểm tra user chưa claim promotion này
 * - Kiểm tra usage limit (nếu có)
 */
private void autoClaimCouponIfNeeded(Long userId, Promotion promotion) {
    // Check nếu user đã claim rồi thì bỏ qua
    if (userPromotionRepository.existsByUserIdAndPromotionId(userId, promotion.getPromotionId())) {
        return;
    }

    // Check usage limit (nếu có)
    if (promotion.getUsageLimit() != null && promotion.getUsageLimit() > 0) {
        long usedCount = userPromotionRepository.countUsedByPromotionId(promotion.getPromotionId());
        if (usedCount >= promotion.getUsageLimit()) {
            throw new RuntimeException("Promotion usage limit reached");
        }
    }

    // Get user profile
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

    if (!(user instanceof Profile profile)) {
        throw new RuntimeException("User must be a Profile instance for claiming promotion");
    }

    // Tự động claim cho user
    UserPromotion userPromotion = new UserPromotion(profile, promotion);
    userPromotionRepository.save(userPromotion);

    log.info("Auto-claimed COUPON promotion '{}' for user {}", promotion.getCode(), userId);

    // Create audit log for auto-claim
    try {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("AUTO_CLAIM_COUPON");
        auditLog.setTargetEntity("USER_PROMOTION");
        auditLog.setTargetId(promotion.getPromotionId());
        auditLog.setDetails(String.format(
                "{\"user_id\":%d,\"promotion_id\":%d,\"promotion_code\":\"%s\",\"discount_value\":%.2f,\"action\":\"auto_claim_coupon_on_use\"}",
                userId, promotion.getPromotionId(), promotion.getCode(), promotion.getDiscountValue()));
        auditLog.setUser(profile);
        auditLogService.save(auditLog);
    } catch (Exception e) {
        log.error("Failed to create audit log for auto-claim coupon: {}", e.getMessage());
    }
}
```

#### 2. Updated `validateAndApplyPromotion()`
```java
if (promotion.getPromotionType() == PromotionType.coupon) {
    // COUPON: Tự động claim nếu user chưa claim
    autoClaimCouponIfNeeded(userId, promotion);
    
    // Check user đã claim và chưa sử dụng
    if (!canUsePromotion(userId, promotionCode)) {
        throw new RuntimeException("Promotion not available for this user");
    }
}
```

#### 3. Updated `validateAndApplyPromotionById()`
```java
if (promotion.getPromotionType() == PromotionType.coupon) {
    // COUPON: Tự động claim nếu user chưa claim
    autoClaimCouponIfNeeded(userId, promotion);
    
    // Check user đã claim và chưa sử dụng
    if (!canUsePromotion(userId, promotion.getCode())) {
        throw new RuntimeException("Promotion not available for this user");
    }
}
```

---

## 🔄 Flow mới

### Before (Old Flow):
```
User enters promotion code
    ↓
System validates code exists
    ↓
Check if user claimed? → NO → ❌ Error: "Promotion not available"
```

### After (New Flow):
```
User enters promotion code
    ↓
System validates code exists (status, date, min order, usage limit)
    ↓
Check if user claimed?
    ├─ YES → Proceed to apply
    └─ NO  → Auto-claim for user
               ↓
             Proceed to apply
    ↓
Check if user used? 
    ├─ NO  → ✅ Apply promotion
    └─ YES → ❌ Error: "Already used"
```

---

## 📊 Validation Logic

### 1. Promotion Conditions Check (BEFORE auto-claim):
- ✅ Status = `active`
- ✅ `startDate <= now <= endDate`
- ✅ `orderValue >= minOrderValue`
- ✅ `usedCount < usageLimit` (if limit exists)

### 2. Auto-Claim Check:
- ✅ User chưa claim promotion này
- ✅ Promotion chưa đạt usage limit

### 3. Can Use Check (AFTER auto-claim):
- ✅ User đã claim (sau khi auto-claim sẽ có record)
- ✅ User chưa sử dụng (`is_used = false`)
- ✅ Promotion còn hiệu lực

---

## 🎯 Benefits

### ✅ Improved UX:
- User chỉ cần nhập code → Hệ thống tự động claim
- Không cần thao tác claim thủ công
- Giảm friction trong checkout flow

### ✅ Business Logic:
- Vẫn maintain usage limit per user (1 lần/user)
- Vẫn maintain global usage limit
- Audit log đầy đủ cho auto-claim actions

### ✅ Backward Compatible:
- User đã claim trước vẫn dùng bình thường
- Không ảnh hưởng đến AUTO promotions
- Không ảnh hưởng đến data cũ

---

## 🧪 Testing Scenarios

### ✅ Test Case 1: User chưa claim COUPON
```http
POST /api/bookings
{
  "discountCode": "7VHB9F",
  "totalAmount": 535000,
  ...
}
```
**Expected:** 
- ✅ Auto-claim promotion
- ✅ Apply discount successfully
- ✅ Create audit log: `AUTO_CLAIM_COUPON`

### ✅ Test Case 2: User đã claim nhưng chưa dùng
```http
POST /api/bookings
{
  "discountCode": "7VHB9F",
  ...
}
```
**Expected:**
- ✅ Skip auto-claim (đã claim rồi)
- ✅ Apply discount successfully

### ✅ Test Case 3: User đã dùng promotion
```http
POST /api/bookings
{
  "discountCode": "7VHB9F",
  ...
}
```
**Expected:**
- ❌ Error: "Promotion not available for this user"

### ✅ Test Case 4: Promotion đạt usage limit
```http
POST /api/bookings
{
  "discountCode": "LIMITED_CODE",
  ...
}
```
**Expected:**
- ❌ Error: "Promotion usage limit reached"

---

## 📝 Database Changes

### `user_promotions` table:
Mỗi lần auto-claim sẽ tạo record:
```sql
INSERT INTO user_promotions (user_id, promotion_id, is_used, claimed_at)
VALUES (30, 15, false, NOW());
```

### `audit_logs` table:
Mỗi lần auto-claim sẽ tạo audit log:
```sql
INSERT INTO audit_logs (action, target_entity, target_id, details, user_id, created_at)
VALUES (
  'AUTO_CLAIM_COUPON', 
  'USER_PROMOTION', 
  15,
  '{"user_id":30,"promotion_id":15,"promotion_code":"7VHB9F","discount_value":50.00,"action":"auto_claim_coupon_on_use"}',
  30,
  NOW()
);
```

---

## 🔍 Monitoring & Logs

### Application Logs:
```
2025-11-05 15:30:00 INFO  PromotionServiceImpl - Auto-claimed COUPON promotion '7VHB9F' for user 30
```

### Audit Log Query:
```sql
SELECT * FROM audit_logs 
WHERE action = 'AUTO_CLAIM_COUPON' 
  AND target_entity = 'USER_PROMOTION'
ORDER BY created_at DESC;
```

---

## 🚨 Edge Cases Handled

1. **Concurrent Requests:**
   - `existsByUserIdAndPromotionId()` check trước khi insert
   - Database unique constraint: `UNIQUE(user_id, promotion_id)`

2. **Usage Limit Race Condition:**
   - Check `countUsedByPromotionId()` trong transaction
   - Nếu exceed limit → throw exception trước khi claim

3. **Invalid User Type:**
   - Validate `user instanceof Profile`
   - Throw exception nếu không phải Profile

4. **Audit Log Failure:**
   - Catch exception, log error
   - Không fail transaction chính

---

## 📚 Related Files

- `PromotionServiceImpl.java` (updated)
- `promotion-auto-claim-coupon-test.http` (new test file)
- `AUTO_CLAIM_COUPON_LOGIC.md` (this file)

---

## 🎉 Summary

**Problem:** User không thể dùng promotion code vì chưa claim

**Solution:** Tự động claim COUPON khi user apply code lần đầu

**Impact:** 
- ✅ Improved UX - 1-step checkout với promotion
- ✅ Maintain business rules (usage limit, validation)
- ✅ Full audit trail
- ✅ Backward compatible

**Status:** ✅ **DEPLOYED & TESTED**

# Hướng Dẫn Frontend Implement ZaloPay QR Code Payment

## 📋 Tổng Quan

ZaloPay Sandbox đã thay đổi policy (tháng 12/2025) và **không còn hỗ trợ redirect thanh toán trên web browser** do:
- Mixed Content Policy (HTTPS/HTTP conflict)
- pmcId 44 (ATM Card) bị chặn trên browser
- CORS Policy chặn requests từ browser

**Giải pháp duy nhất:** Hiển thị **QR Code** để user quét bằng ZaloPay app.

---

## 🔄 Thay Đổi API Response

### Backend Response Mới (từ `/api/payments/create`):

```json
{
  "code": 200,
  "message": "Payment created successfully",
  "result": {
    "paymentId": 379,
    "status": "pending",
    "paymentUrl": null,
    "qrCode": "00020101021226530010vn.zalopay...",  // ← MỚI: QR Code data
    "appDeepLink": "https://qcgateway.zalopay.vn/openinapp?order=...",  // ← MỚI: Deep link
    "bookingId": 487,
    "bookingIds": [487]
  }
}
```

### Fields Mới:

| Field | Type | Mô Tả |
|-------|------|-------|
| `qrCode` | String | QR Code data để hiển thị (VietQR format) |
| `appDeepLink` | String | Deep link để mở ZaloPay app (mobile only) |
| `paymentUrl` | String/null | `null` cho ZaloPay, có giá trị cho PayPal |

---

## 🎯 Implementation Guide

### 1. Cài Đặt Thư Viện

```bash
# React/Next.js
npm install qrcode.react

# Hoặc vanilla JS
npm install qrcode
```

### 2. Component Hiển Thị QR Code (React/Next.js)

```jsx
// components/ZaloPayQRModal.jsx
import { useEffect, useState } from 'react';
import QRCode from 'qrcode.react';
import axios from 'axios';

export default function ZaloPayQRModal({ qrCode, paymentId, appDeepLink, onSuccess }) {
  const [status, setStatus] = useState('waiting'); // waiting, checking, completed, failed
  const [message, setMessage] = useState('Vui lòng quét mã QR bằng app ZaloPay');

  useEffect(() => {
    // Polling để check payment status mỗi 3 giây
    const interval = setInterval(async () => {
      try {
        setStatus('checking');
        const response = await axios.post(
          `/api/payments/zalopay/confirm/${paymentId}`
        );
        
        if (response.data.result?.status === 'completed') {
          setStatus('completed');
          setMessage('Thanh toán thành công!');
          clearInterval(interval);
          
          // Redirect sau 2 giây
          setTimeout(() => {
            onSuccess(paymentId);
            // window.location.href = `/bookingresult/${paymentId}`;
          }, 2000);
        }
      } catch (error) {
        console.error('Check payment status error:', error);
        // Tiếp tục polling nếu lỗi network
      }
    }, 3000);

    // Cleanup sau 10 phút (timeout)
    const timeout = setTimeout(() => {
      clearInterval(interval);
      if (status !== 'completed') {
        setStatus('failed');
        setMessage('Hết thời gian chờ thanh toán');
      }
    }, 600000); // 10 phút

    return () => {
      clearInterval(interval);
      clearTimeout(timeout);
    };
  }, [paymentId]);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-8 max-w-md w-full">
        {/* Header */}
        <h2 className="text-2xl font-bold text-center mb-4">
          Thanh toán ZaloPay
        </h2>

        {/* QR Code */}
        <div className="flex justify-center mb-6">
          <QRCode 
            value={qrCode} 
            size={280}
            level="H"
            includeMargin={true}
          />
        </div>

        {/* Hướng dẫn */}
        <div className="text-center mb-4">
          <p className="text-gray-600 mb-2">{message}</p>
          
          {status === 'checking' && (
            <div className="flex items-center justify-center gap-2">
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-500"></div>
              <span className="text-sm text-blue-500">Đang kiểm tra...</span>
            </div>
          )}

          {status === 'completed' && (
            <div className="text-green-600 font-semibold">
              ✓ Thanh toán thành công
            </div>
          )}
        </div>

        {/* Button mở app (chỉ hiện trên mobile) */}
        <div className="md:hidden mb-4">
          <a 
            href={appDeepLink}
            className="block w-full bg-blue-600 text-white text-center py-3 rounded-lg hover:bg-blue-700 transition"
          >
            Mở trong ZaloPay App
          </a>
        </div>

        {/* Hướng dẫn chi tiết */}
        <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600">
          <p className="font-semibold mb-2">Cách thanh toán:</p>
          <ol className="list-decimal list-inside space-y-1">
            <li>Mở app ZaloPay trên điện thoại</li>
            <li>Chọn "Quét mã QR"</li>
            <li>Quét mã QR hiển thị trên màn hình</li>
            <li>Xác nhận thanh toán</li>
          </ol>
        </div>
      </div>
    </div>
  );
}
```

### 3. Integration vào Payment Flow

```jsx
// pages/payment.jsx hoặc components/BookingPayment.jsx
import { useState } from 'react';
import ZaloPayQRModal from '@/components/ZaloPayQRModal';

export default function PaymentPage() {
  const [showQRModal, setShowQRModal] = useState(false);
  const [paymentData, setPaymentData] = useState(null);

  const handlePayment = async (paymentMethod) => {
    try {
      const response = await axios.post('/api/payments/create', {
        bookingIds: [bookingId],
        paymentMethod: paymentMethod // 'ZALOPAY' hoặc 'PAYPAL'
      });

      const result = response.data.result;

      // Check xem có QR Code không (ZaloPay)
      if (result.qrCode) {
        // Hiển thị QR Modal
        setPaymentData({
          qrCode: result.qrCode,
          paymentId: result.paymentId,
          appDeepLink: result.appDeepLink
        });
        setShowQRModal(true);
      } else if (result.paymentUrl) {
        // Redirect cho PayPal
        window.location.href = result.paymentUrl;
      } else {
        throw new Error('Invalid payment response');
      }
    } catch (error) {
      console.error('Payment creation error:', error);
      alert('Có lỗi xảy ra khi tạo thanh toán');
    }
  };

  const handlePaymentSuccess = (paymentId) => {
    setShowQRModal(false);
    // Redirect đến trang kết quả
    window.location.href = `/bookingresult/${paymentId}`;
  };

  return (
    <div>
      {/* Payment Method Buttons */}
      <button 
        onClick={() => handlePayment('ZALOPAY')}
        className="btn-zalopay"
      >
        Thanh toán ZaloPay
      </button>

      <button 
        onClick={() => handlePayment('PAYPAL')}
        className="btn-paypal"
      >
        Thanh toán PayPal
      </button>

      {/* QR Modal */}
      {showQRModal && paymentData && (
        <ZaloPayQRModal
          qrCode={paymentData.qrCode}
          paymentId={paymentData.paymentId}
          appDeepLink={paymentData.appDeepLink}
          onSuccess={handlePaymentSuccess}
        />
      )}
    </div>
  );
}
```

### 4. Vanilla JavaScript (không dùng React)

```html
<!-- Thêm thư viện QRCode.js -->
<script src="https://cdn.jsdelivr.net/npm/qrcodejs@1.0.0/qrcode.min.js"></script>

<div id="qrCodeModal" style="display:none;">
  <div id="qrcode"></div>
  <p id="statusMessage">Vui lòng quét mã QR</p>
  <a id="openAppBtn" style="display:none;">Mở ZaloPay App</a>
</div>

<script>
async function createPayment(paymentMethod) {
  const response = await fetch('/api/payments/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      bookingIds: [bookingId],
      paymentMethod: paymentMethod
    })
  });

  const data = await response.json();
  const result = data.result;

  if (result.qrCode) {
    // Hiển thị QR Code
    showQRModal(result.qrCode, result.paymentId, result.appDeepLink);
  } else if (result.paymentUrl) {
    window.location.href = result.paymentUrl;
  }
}

function showQRModal(qrCodeData, paymentId, appDeepLink) {
  const modal = document.getElementById('qrCodeModal');
  const qrDiv = document.getElementById('qrcode');
  const openAppBtn = document.getElementById('openAppBtn');

  // Clear previous QR
  qrDiv.innerHTML = '';

  // Generate QR Code
  new QRCode(qrDiv, {
    text: qrCodeData,
    width: 280,
    height: 280
  });

  // Show modal
  modal.style.display = 'block';

  // Set deep link (mobile only)
  openAppBtn.href = appDeepLink;
  openAppBtn.style.display = isMobile() ? 'block' : 'none';

  // Start polling
  pollPaymentStatus(paymentId);
}

function pollPaymentStatus(paymentId) {
  const interval = setInterval(async () => {
    try {
      const response = await fetch(`/api/payments/zalopay/confirm/${paymentId}`, {
        method: 'POST'
      });
      const data = await response.json();

      if (data.result?.status === 'completed') {
        clearInterval(interval);
        document.getElementById('statusMessage').textContent = 'Thanh toán thành công!';
        
        setTimeout(() => {
          window.location.href = `/bookingresult/${paymentId}`;
        }, 2000);
      }
    } catch (error) {
      console.error('Check payment error:', error);
    }
  }, 3000);

  // Timeout sau 10 phút
  setTimeout(() => clearInterval(interval), 600000);
}

function isMobile() {
  return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
}
</script>
```

---

## 🧪 Testing

### Test trên Desktop:
1. Click "Thanh toán ZaloPay"
2. QR Code xuất hiện trên màn hình
3. Mở app ZaloPay trên điện thoại
4. Quét QR Code
5. Xác nhận thanh toán trong app
6. Web tự động redirect sau khi thanh toán thành công

### Test trên Mobile Browser:
1. Click "Thanh toán ZaloPay"
2. QR Code xuất hiện
3. Click button "Mở trong ZaloPay App"
4. App ZaloPay mở ra
5. Xác nhận thanh toán
6. Quay lại browser → auto redirect

### Test với Localhost:

**⚠️ QUAN TRỌNG: Quét QR Code KHÔNG CẦN backend và điện thoại cùng mạng!**

- QR code chứa thông tin thanh toán → ZaloPay app gửi đến **ZaloPay server**
- Backend chỉ cần accessible cho **frontend polling** hoặc **ZaloPay callback**

**Recommended Flow (Dev/Test):**
1. ✅ **Backend**: `http://localhost:8080` (không cần public URL)
2. ✅ **Frontend**: Cùng máy hoặc cùng mạng WiFi với backend
3. ✅ **Polling**: Frontend call `/api/payments/zalopay/confirm/{paymentId}` mỗi 3s
4. ✅ **Điện thoại**: Quét QR từ bất kỳ đâu (không cần cùng mạng)

**Production Flow (Callback):**
- Cần **public URL** (domain/ngrok) để ZaloPay callback về backend
- Update `redirecturl` trong backend từ `localhost` → `https://your-domain.com`

---

## ⚠️ Lưu Ý Quan Trọng

1. **Không redirect window.location.href** đến `order_url` nữa → sẽ lỗi CORS
2. **Phải dùng QR Code** - không có cách nào khác với ZaloPay sandbox
3. **Polling interval**: 3-5 giây là tối ưu (quá nhanh → spam server)
4. **Timeout**: Set timeout 10 phút để tránh polling vô hạn
5. **Mobile detection**: Chỉ hiển thị button "Mở App" trên mobile

---

## 📞 API Endpoints

### 1. Tạo Payment
```
POST /api/payments/create
Content-Type: application/json

{
  "bookingIds": [487],
  "paymentMethod": "ZALOPAY"
}
```

### 2. Check Payment Status (Polling)
```
POST /api/payments/zalopay/confirm/{paymentId}

Response:
{
  "code": 200,
  "result": {
    "paymentId": 379,
    "status": "completed",  // pending | completed | failed
    "bookingId": 487
  }
}
```

---

## 🎨 UI/UX Recommendations

1. **Loading State**: Hiển thị spinner khi đang generate QR
2. **Status Indicator**: Show "Đang kiểm tra..." khi polling
3. **Success Animation**: Hiển thị checkmark khi completed
4. **Error Handling**: Message rõ ràng khi timeout/failed
5. **Responsive Design**: QR modal phải responsive trên mọi device
6. **Close Button**: Cho phép user đóng modal và thử lại

---

## 📚 Resources

- [QRCode.react Documentation](https://github.com/zpao/qrcode.react)
- [ZaloPay Integration Guide](https://docs.zalopay.vn/v2/)
- Backend API: `http://localhost:8080/swagger-ui/index.html`

---

## 🐛 Troubleshooting

### QR Code không hiển thị
- Check console: có lỗi parse `qrCode` string không?
- Verify response từ backend có field `qrCode`

### Polling không hoạt động
- Check CORS: frontend và backend cùng domain?
- Check network tab: API `/confirm/{paymentId}` có được gọi không?

### Deep link không mở app
- Chỉ work trên mobile device thật/emulator
- Không work trên desktop browser

### Callback không hoạt động (localhost)
- ✅ **Bình thường!** ZaloPay server không thể call localhost
- ✅ Dùng **Polling** thay vì callback (đã implement trong component)
- 🔧 Production: Cần public URL + update `redirecturl` trong backend

### Quét QR nhưng payment không update
- Check polling có chạy không (xem network tab)
- Verify backend API `/confirm/{paymentId}` hoạt động
- Có thể thanh toán đang pending - chờ 3-5s để polling check

---

## 🌐 Network Configuration

### Development (Localhost):
```
[Frontend] ← same machine/LAN → [Backend localhost:8080]
     ↓ (polling /confirm)
[Backend] → Query ZaloPay → Check payment status
     ↑
[Phone] → Scan QR → [ZaloPay Server] → Process payment
```
✅ **Không cần** điện thoại cùng mạng với backend để quét QR

### Production:
```
[Frontend] → [Backend https://domain.com]
     ↓ (polling /confirm)          ↑ (callback)
[Backend] ← [ZaloPay Server] ← [Phone scan QR]
```
✅ Cần public domain để nhận callback (optional nếu dùng polling)

---

**Cập nhật:** 04/01/2026  
**Backend API Version:** v1.0  
**Status:** ✅ Ready for Implementation

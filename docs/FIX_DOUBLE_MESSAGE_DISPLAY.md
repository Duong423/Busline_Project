# 🐛 Fix Double Message Display (Hiển thị tin nhắn trùng)

## ❓ Vấn đề

Tin nhắn hiển thị **2 lần** khi gửi:
1. Lần 1: Ngay khi click Send (optimistic UI)
2. Lần 2: Khi nhận broadcast từ server

## 🔍 Nguyên nhân

```javascript
// Frontend code hiện tại (SAI)
const sendMessage = () => {
  const newMessage = {
    content: inputText,
    sender: currentUser,
    type: 'CHAT',
    timestamp: new Date().toISOString()
  };
  
  // BỨC 1: Hiển thị ngay (optimistic UI)
  setMessages([...messages, newMessage]); // ← Lần 1
  
  // BƯỚC 2: Gửi lên server
  stompClient.send(`/app/chat.sendMessage/${roomId}`, {}, JSON.stringify(newMessage));
};

// WebSocket subscription
stompClient.subscribe(`/topic/public/${roomId}`, (message) => {
  const receivedMessage = JSON.parse(message.body);
  
  // BƯỚC 3: Nhận lại từ server và hiển thị
  setMessages([...messages, receivedMessage]); // ← Lần 2 (TRÙNG!)
});
```

**Kết quả**: Tin nhắn hiển thị 2 lần!

---

## ✅ GIẢI PHÁP 1: Bỏ Optimistic UI (KHUYẾN NGHỊ)

**Ưu điểm**: Đơn giản, không cần logic phức tạp  
**Nhược điểm**: UX hơi chậm (đợi server response)

### Code sửa:

```javascript
const sendMessage = () => {
  const newMessage = {
    content: inputText,
    sender: currentUser,
    type: 'CHAT',
    timestamp: new Date().toISOString()
  };
  
  // ❌ KHÔNG hiển thị ngay
  // setMessages([...messages, newMessage]); // BỎ DÒNG NÀY
  
  // ✅ CHỈ gửi lên server
  stompClient.send(`/app/chat.sendMessage/${roomId}`, {}, JSON.stringify(newMessage));
  
  // Clear input
  setInputText('');
};

// WebSocket subscription (GIỮ NGUYÊN)
stompClient.subscribe(`/topic/public/${roomId}`, (message) => {
  const receivedMessage = JSON.parse(message.body);
  
  // ✅ CHỈ hiển thị khi nhận từ server
  setMessages(prev => [...prev, receivedMessage]);
});
```

---

## ✅ GIẢI PHÁP 2: Optimistic UI + Deduplication (UX TỐT HƠN)

**Ưu điểm**: UX tốt, hiển thị ngay  
**Nhược điểm**: Phức tạp hơn, cần tracking message ID

### Code sửa:

```javascript
// Thêm unique ID cho mỗi message
const sendMessage = () => {
  const tempId = `temp-${Date.now()}-${Math.random()}`; // Temporary ID
  
  const newMessage = {
    id: tempId, // ← Thêm ID tạm
    content: inputText,
    sender: currentUser,
    type: 'CHAT',
    timestamp: new Date().toISOString(),
    isPending: true // ← Flag đánh dấu đang gửi
  };
  
  // BƯỚC 1: Hiển thị ngay với pending state
  setMessages(prev => [...prev, newMessage]);
  
  // BƯỚC 2: Gửi lên server (KÈM tempId để tracking)
  stompClient.send(`/app/chat.sendMessage/${roomId}`, {}, JSON.stringify({
    ...newMessage,
    tempId: tempId // ← Gửi kèm tempId
  }));
  
  setInputText('');
};

// WebSocket subscription với deduplication
stompClient.subscribe(`/topic/public/${roomId}`, (message) => {
  const receivedMessage = JSON.parse(message.body);
  
  setMessages(prev => {
    // BƯỚC 3: Kiểm tra nếu là tin nhắn của mình (dựa vào tempId)
    if (receivedMessage.tempId) {
      // Thay thế message tạm bằng message thật từ server
      return prev.map(msg => 
        msg.id === receivedMessage.tempId 
          ? { ...receivedMessage, isPending: false } // Cập nhật message
          : msg
      );
    } else {
      // Tin nhắn từ người khác, thêm mới
      return [...prev, receivedMessage];
    }
  });
});
```

### Hiển thị pending state:

```jsx
{messages.map(msg => (
  <div 
    key={msg.id} 
    className={`message ${msg.isPending ? 'opacity-50' : ''}`}
  >
    {msg.content}
    {msg.isPending && <span className="text-xs">Đang gửi...</span>}
  </div>
))}
```

---

## ✅ GIẢI PHÁP 3: Lọc tin nhắn của chính mình (ĐƠN GIẢN)

```javascript
stompClient.subscribe(`/topic/public/${roomId}`, (message) => {
  const receivedMessage = JSON.parse(message.body);
  
  // ❌ BỎ QUA nếu là tin nhắn của chính mình
  if (receivedMessage.sender === currentUser) {
    console.log('Bỏ qua tin nhắn của chính mình từ broadcast');
    return; // KHÔNG thêm vào messages
  }
  
  // ✅ CHỈ hiển thị tin nhắn từ người khác
  setMessages(prev => [...prev, receivedMessage]);
});
```

**⚠️ LƯU Ý**: Cách này chỉ hoạt động nếu frontend có optimistic UI.

---

## 🎯 KHUYẾN NGHỊ

### Cho dự án nhỏ/vừa:
→ **GIẢI PHÁP 1** (Bỏ optimistic UI)  
→ Đơn giản, ít lỗi

### Cho dự án lớn/production:
→ **GIẢI PHÁP 2** (Optimistic UI + Deduplication)  
→ UX tốt nhất, professional

---

## 📝 Backend Changes (ĐÃ SỬA)

Backend đã được cập nhật để **BẮT BUỘC** broadcast đến tất cả clients (kể cả người gửi).

File: `ChatController.java`
```java
@MessageMapping("/chat.sendMessage/{roomId}")
public void sendMessage(@DestinationVariable String roomId, @Payload ChatMessageDTO chatMessage) {
    ChatMessage savedMessage = chatService.saveMessage(chatMessage, roomId);
    
    // ✅ Broadcast đến TẤT CẢ (kể cả người gửi)
    messagingTemplate.convertAndSend("/topic/public/" + roomId, savedMessage);
}
```

→ **Frontend phải tự xử lý để tránh hiển thị trùng!**

---

## 🧪 Testing

### Test case:
1. User A gửi tin nhắn "Hello"
2. ✅ User A thấy "Hello" **1 LẦN**
3. ✅ User B thấy "Hello" **1 LẦN**
4. User B gửi tin nhắn "Hi"
5. ✅ User B thấy "Hi" **1 LẦN**
6. ✅ User A thấy "Hi" **1 LẦN**

---

## 📚 Tài liệu tham khảo

- [WebSocket Best Practices](https://www.baeldung.com/websockets-spring)
- [Optimistic UI Pattern](https://www.apollographql.com/docs/react/performance/optimistic-ui/)

# 🤖 Hướng Dẫn Triển Khai Smart Chatbot - Frontend Next.js

## 📋 Mục Lục
1. [Cấu trúc dự án](#cấu-trúc-dự-án)
2. [Cài đặt dependencies](#cài-đặt-dependencies)
3. [Tạo types/interfaces](#tạo-typesinterfaces)
4. [WebSocket client setup](#websocket-client-setup)
5. [Components](#components)
6. [API hooks](#api-hooks)
7. [Usage example](#usage-example)

---

## 1. Cấu trúc Dự Án

```
src/
├── components/
│   └── chat/
│       ├── ChatWindow.tsx           # Main chat component
│       ├── MessageBubble.tsx        # Message display component
│       ├── TripCard.tsx             # Product card component
│       ├── SearchResults.tsx        # Search results grid
│       ├── ChatInput.tsx            # Input component
│       └── SuggestedQuestions.tsx   # Suggested questions
├── hooks/
│   ├── useChatWebSocket.ts         # WebSocket hook
│   └── useChatAPI.ts               # REST API hook
├── services/
│   ├── chatService.ts              # Chat API service
│   └── websocketService.ts         # WebSocket service
├── types/
│   └── chat.types.ts               # TypeScript types
└── utils/
    └── chatHelpers.ts              # Helper functions
```

---

## 2. Cài Đặt Dependencies

```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client

# Optional: UI libraries
npm install framer-motion lucide-react
npm install date-fns
```

---

## 3. Tạo Types/Interfaces

### `src/types/chat.types.ts`

```typescript
// Response types matching backend DTOs

export interface SearchIntent {
  intentType: 'SEARCH_TRIP' | 'BOOK_TICKET' | 'ASK_PRICE' | 'ASK_SCHEDULE' | 'GENERAL_QUESTION';
  departure?: string;
  destination?: string;
  departureDate?: string;
  numberOfTickets?: number;
  busType?: string;
  priceMin?: number;
  priceMax?: number;
  confidence?: number;
  additionalInfo?: string;
}

export interface TripSearchResult {
  tripId: number;
  routeName: string;
  departureLocation: string;
  arrivalLocation: string;
  departureTime: string;
  arrivalTime: string;
  price: number;
  availableSeats: number;
  busType: string;
  busPlate: string;
  amenities: string[];
  rating?: number;
  hasPromotion: boolean;
  discountedPrice?: number;
  imageUrl?: string;
}

export type AIResponseType = 
  | 'TEXT' 
  | 'PRODUCT_SEARCH' 
  | 'BOOKING_GUIDE' 
  | 'ERROR' 
  | 'NEED_MORE_INFO';

export interface AIResponse {
  messageId?: number;
  content: string;
  type: AIResponseType;
  searchIntent?: SearchIntent;
  trips?: TripSearchResult[];
  totalResults?: number;
  needMoreInfo?: boolean;
  suggestedQuestions?: string[];
  timestamp: number;
}

export interface ChatMessage {
  id?: number;
  content: string;
  sender: string;
  recipient?: string;
  timestamp: number;
  type: 'user' | 'bot';
  aiResponse?: AIResponse;
}
```

---

## 4. WebSocket Client Setup

### `src/services/websocketService.ts`

```typescript
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { AIResponse } from '@/types/chat.types';

export class ChatWebSocketService {
  private client: Client | null = null;
  private connected: boolean = false;
  private userId: string;

  constructor(userId: string) {
    this.userId = userId;
  }

  connect(onMessageReceived: (message: AIResponse) => void): Promise<void> {
    return new Promise((resolve, reject) => {
      // WebSocket endpoint - adjust to your backend URL
      const socketUrl = `${process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080'}/ws`;

      this.client = new Client({
        webSocketFactory: () => new SockJS(socketUrl),
        
        onConnect: () => {
          console.log('✅ Connected to WebSocket');
          this.connected = true;

          // Subscribe to smart chat topic
          this.client?.subscribe(`/topic/smart/${this.userId}`, (message: IMessage) => {
            try {
              const aiResponse: AIResponse = JSON.parse(message.body);
              onMessageReceived(aiResponse);
            } catch (error) {
              console.error('Error parsing message:', error);
            }
          });

          resolve();
        },

        onStompError: (frame) => {
          console.error('❌ WebSocket error:', frame);
          this.connected = false;
          reject(frame);
        },

        onDisconnect: () => {
          console.log('🔌 Disconnected from WebSocket');
          this.connected = false;
        },

        // Heartbeat configuration
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,

        // Reconnect configuration
        reconnectDelay: 5000,
      });

      this.client.activate();
    });
  }

  sendMessage(message: string, sender: string): void {
    if (!this.connected || !this.client) {
      console.error('❌ WebSocket not connected');
      return;
    }

    const chatMessage = {
      content: message,
      sender: sender,
      type: 'CHAT',
      timestamp: Date.now(),
    };

    this.client.publish({
      destination: `/app/chat.smart/${this.userId}`,
      body: JSON.stringify(chatMessage),
    });
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.connected = false;
    }
  }

  isConnected(): boolean {
    return this.connected;
  }
}
```

---

## 5. Components

### `src/components/chat/TripCard.tsx`

```typescript
'use client';

import { TripSearchResult } from '@/types/chat.types';
import { motion } from 'framer-motion';
import { Clock, MapPin, Users, Star, Tag } from 'lucide-react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';

interface TripCardProps {
  trip: TripSearchResult;
  onBook?: (tripId: number) => void;
}

export default function TripCard({ trip, onBook }: TripCardProps) {
  const departureTime = new Date(trip.departureTime);
  const arrivalTime = new Date(trip.arrivalTime);
  
  const finalPrice = trip.hasPromotion && trip.discountedPrice 
    ? trip.discountedPrice 
    : trip.price;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white rounded-lg shadow-md hover:shadow-lg transition-shadow p-4 border border-gray-200"
    >
      {/* Header */}
      <div className="flex justify-between items-start mb-3">
        <div>
          <h3 className="font-semibold text-lg text-gray-900">{trip.routeName}</h3>
          <span className="inline-block px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded-full mt-1">
            {trip.busType}
          </span>
        </div>
        {trip.rating && (
          <div className="flex items-center gap-1 text-yellow-500">
            <Star size={16} fill="currentColor" />
            <span className="text-sm font-medium">{trip.rating}</span>
          </div>
        )}
      </div>

      {/* Route */}
      <div className="flex items-center gap-2 mb-3 text-sm text-gray-600">
        <MapPin size={16} />
        <span>{trip.departureLocation}</span>
        <span>→</span>
        <span>{trip.arrivalLocation}</span>
      </div>

      {/* Time */}
      <div className="flex items-center gap-2 mb-3 text-sm">
        <Clock size={16} className="text-gray-400" />
        <span className="font-medium">{format(departureTime, 'HH:mm', { locale: vi })}</span>
        <span className="text-gray-400">-</span>
        <span className="font-medium">{format(arrivalTime, 'HH:mm', { locale: vi })}</span>
      </div>

      {/* Seats */}
      <div className="flex items-center gap-2 mb-3 text-sm">
        <Users size={16} className="text-gray-400" />
        <span className="text-gray-600">Còn {trip.availableSeats} ghế trống</span>
      </div>

      {/* Amenities */}
      {trip.amenities && trip.amenities.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {trip.amenities.slice(0, 3).map((amenity, index) => (
            <span key={index} className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">
              {amenity}
            </span>
          ))}
        </div>
      )}

      {/* Price & Action */}
      <div className="flex justify-between items-center pt-3 border-t">
        <div>
          {trip.hasPromotion && trip.discountedPrice ? (
            <>
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-red-600">
                  {finalPrice.toLocaleString('vi-VN')}đ
                </span>
                <Tag size={16} className="text-red-600" />
              </div>
              <span className="text-sm text-gray-400 line-through">
                {trip.price.toLocaleString('vi-VN')}đ
              </span>
            </>
          ) : (
            <span className="text-lg font-bold text-gray-900">
              {trip.price.toLocaleString('vi-VN')}đ
            </span>
          )}
        </div>

        <button
          onClick={() => onBook?.(trip.tripId)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
        >
          Đặt vé
        </button>
      </div>
    </motion.div>
  );
}
```

### `src/components/chat/SearchResults.tsx`

```typescript
'use client';

import { TripSearchResult } from '@/types/chat.types';
import TripCard from './TripCard';
import { motion } from 'framer-motion';

interface SearchResultsProps {
  trips: TripSearchResult[];
  onBookTrip?: (tripId: number) => void;
}

export default function SearchResults({ trips, onBookTrip }: SearchResultsProps) {
  if (trips.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-3 mt-4"
    >
      <h4 className="text-sm font-medium text-gray-700 mb-2">
        Kết quả tìm kiếm ({trips.length} chuyến)
      </h4>
      
      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-1">
        {trips.map((trip) => (
          <TripCard 
            key={trip.tripId} 
            trip={trip} 
            onBook={onBookTrip}
          />
        ))}
      </div>
    </motion.div>
  );
}
```

### `src/components/chat/MessageBubble.tsx`

```typescript
'use client';

import { ChatMessage } from '@/types/chat.types';
import { motion } from 'framer-motion';
import { Bot, User } from 'lucide-react';
import { format } from 'date-fns';
import SearchResults from './SearchResults';
import ReactMarkdown from 'react-markdown';

interface MessageBubbleProps {
  message: ChatMessage;
  onBookTrip?: (tripId: number) => void;
}

export default function MessageBubble({ message, onBookTrip }: MessageBubbleProps) {
  const isBot = message.type === 'bot';
  const aiResponse = message.aiResponse;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={`flex gap-3 ${isBot ? 'justify-start' : 'justify-end'}`}
    >
      {/* Avatar */}
      {isBot && (
        <div className="flex-shrink-0 w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center">
          <Bot size={18} className="text-white" />
        </div>
      )}

      {/* Message Content */}
      <div className={`max-w-[80%] ${isBot ? '' : 'order-first'}`}>
        {/* Text Bubble */}
        <div
          className={`rounded-2xl px-4 py-3 ${
            isBot
              ? 'bg-gray-100 text-gray-900'
              : 'bg-blue-600 text-white'
          }`}
        >
          {isBot ? (
            <div className="prose prose-sm max-w-none">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
          ) : (
            <p className="text-sm">{message.content}</p>
          )}
        </div>

        {/* Search Results (only for bot messages) */}
        {isBot && aiResponse?.trips && aiResponse.trips.length > 0 && (
          <SearchResults 
            trips={aiResponse.trips} 
            onBookTrip={onBookTrip}
          />
        )}

        {/* Suggested Questions */}
        {isBot && aiResponse?.suggestedQuestions && aiResponse.suggestedQuestions.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-2">
            {aiResponse.suggestedQuestions.map((question, index) => (
              <button
                key={index}
                className="text-xs px-3 py-1 bg-white border border-gray-300 rounded-full hover:bg-gray-50 transition-colors"
                onClick={() => {
                  // Handle suggested question click
                  console.log('Suggested question:', question);
                }}
              >
                {question}
              </button>
            ))}
          </div>
        )}

        {/* Timestamp */}
        <div className={`text-xs text-gray-400 mt-1 ${isBot ? '' : 'text-right'}`}>
          {format(message.timestamp, 'HH:mm')}
        </div>
      </div>

      {/* User Avatar */}
      {!isBot && (
        <div className="flex-shrink-0 w-8 h-8 bg-gray-600 rounded-full flex items-center justify-center">
          <User size={18} className="text-white" />
        </div>
      )}
    </motion.div>
  );
}
```

### `src/components/chat/ChatWindow.tsx`

```typescript
'use client';

import { useState, useEffect, useRef } from 'react';
import { ChatMessage, AIResponse } from '@/types/chat.types';
import { ChatWebSocketService } from '@/services/websocketService';
import MessageBubble from './MessageBubble';
import { Send, X } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface ChatWindowProps {
  userId: string;
  onClose?: () => void;
}

export default function ChatWindow({ userId, onClose }: ChatWindowProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isConnecting, setIsConnecting] = useState(true);
  const [isTyping, setIsTyping] = useState(false);
  
  const wsServiceRef = useRef<ChatWebSocketService | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto scroll to bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Initialize WebSocket
  useEffect(() => {
    const wsService = new ChatWebSocketService(userId);
    wsServiceRef.current = wsService;

    wsService.connect((aiResponse: AIResponse) => {
      setIsTyping(false);
      
      // Add bot message
      const botMessage: ChatMessage = {
        content: aiResponse.content,
        sender: 'AI Bot',
        timestamp: aiResponse.timestamp,
        type: 'bot',
        aiResponse: aiResponse,
      };
      
      setMessages(prev => [...prev, botMessage]);
    }).then(() => {
      setIsConnecting(false);
      
      // Send welcome message
      const welcomeMessage: ChatMessage = {
        content: 'Xin chào! Tôi là trợ lý ảo của Busify. Tôi có thể giúp bạn tìm kiếm và đặt vé xe. Bạn muốn đi đâu hôm nay?',
        sender: 'AI Bot',
        timestamp: Date.now(),
        type: 'bot',
      };
      setMessages([welcomeMessage]);
    }).catch(error => {
      console.error('Failed to connect:', error);
      setIsConnecting(false);
    });

    return () => {
      wsService.disconnect();
    };
  }, [userId]);

  // Send message
  const handleSendMessage = () => {
    if (!inputValue.trim() || !wsServiceRef.current?.isConnected()) return;

    // Add user message
    const userMessage: ChatMessage = {
      content: inputValue,
      sender: userId,
      timestamp: Date.now(),
      type: 'user',
    };
    
    setMessages(prev => [...prev, userMessage]);
    
    // Send to backend
    wsServiceRef.current.sendMessage(inputValue, userId);
    
    setInputValue('');
    setIsTyping(true);
  };

  // Handle booking
  const handleBookTrip = (tripId: number) => {
    // Navigate to booking page or open booking modal
    console.log('Book trip:', tripId);
    window.location.href = `/booking/${tripId}`;
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      className="flex flex-col h-[600px] w-full max-w-2xl bg-white rounded-lg shadow-2xl overflow-hidden"
    >
      {/* Header */}
      <div className="bg-blue-600 text-white px-4 py-3 flex justify-between items-center">
        <div>
          <h3 className="font-semibold">Trợ lý ảo Busify</h3>
          <p className="text-xs text-blue-100">
            {isConnecting ? 'Đang kết nối...' : 'Đang hoạt động'}
          </p>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="p-1 hover:bg-blue-700 rounded-full transition-colors"
          >
            <X size={20} />
          </button>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
        {messages.map((message, index) => (
          <MessageBubble
            key={index}
            message={message}
            onBookTrip={handleBookTrip}
          />
        ))}
        
        {isTyping && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex gap-3"
          >
            <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center">
              <div className="w-2 h-2 bg-white rounded-full animate-bounce" />
            </div>
            <div className="bg-gray-100 rounded-2xl px-4 py-3">
              <p className="text-sm text-gray-500">Đang soạn tin...</p>
            </div>
          </motion.div>
        )}
        
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="border-t bg-white p-4">
        <div className="flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
            placeholder="Nhập tin nhắn... (VD: Tìm vé từ Hà Nội đến Đà Nẵng)"
            className="flex-1 px-4 py-2 border border-gray-300 rounded-full focus:outline-none focus:ring-2 focus:ring-blue-500"
            disabled={isConnecting}
          />
          <button
            onClick={handleSendMessage}
            disabled={!inputValue.trim() || isConnecting}
            className="px-6 py-2 bg-blue-600 text-white rounded-full hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
          >
            <Send size={18} />
          </button>
        </div>
      </div>
    </motion.div>
  );
}
```

---

## 6. API Hooks (Alternative to WebSocket)

### `src/hooks/useChatAPI.ts`

```typescript
import { useState } from 'react';
import { AIResponse } from '@/types/chat.types';
import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export function useChatAPI() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sendMessage = async (message: string, token?: string): Promise<AIResponse | null> => {
    try {
      setLoading(true);
      setError(null);

      const response = await axios.post(
        `${API_URL}/api/ai-chat/smart/send`,
        { content: message },
        {
          headers: {
            'Content-Type': 'application/json',
            ...(token && { Authorization: `Bearer ${token}` }),
          },
        }
      );

      return response.data.data as AIResponse;
    } catch (err: any) {
      setError(err.message || 'Failed to send message');
      return null;
    } finally {
      setLoading(false);
    }
  };

  return { sendMessage, loading, error };
}
```

---

## 7. Usage Example

### `app/chat/page.tsx`

```typescript
'use client';

import { useState } from 'react';
import ChatWindow from '@/components/chat/ChatWindow';
import { MessageCircle } from 'lucide-react';

export default function ChatPage() {
  const [isOpen, setIsOpen] = useState(false);
  const userId = 'user@example.com'; // Get from auth context

  return (
    <div className="min-h-screen bg-gray-100 p-4">
      {/* Floating Chat Button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className="fixed bottom-6 right-6 w-14 h-14 bg-blue-600 text-white rounded-full shadow-lg hover:bg-blue-700 transition-colors flex items-center justify-center z-50"
        >
          <MessageCircle size={24} />
        </button>
      )}

      {/* Chat Window */}
      {isOpen && (
        <div className="fixed bottom-6 right-6 z-50">
          <ChatWindow 
            userId={userId}
            onClose={() => setIsOpen(false)}
          />
        </div>
      )}
    </div>
  );
}
```

---

## 🔑 Environment Variables

Create `.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080
```

---

## 📝 Backend API Endpoints

### WebSocket
- **Connect**: `ws://localhost:8080/ws`
- **Subscribe**: `/topic/smart/{userId}`
- **Send**: `/app/chat.smart/{userId}`

### REST API
- **POST** `/api/ai-chat/smart/send` - Send message and get response
- **GET** `/api/ai-chat/status` - Check AI service status

---

## 🎨 Styling

Install Tailwind CSS if not already:

```bash
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

---

## ✅ Features

✨ **Smart Product Search** - AI tự động tìm kiếm vé xe từ câu chat
🔍 **Intent Recognition** - Nhận diện ý định người dùng
💬 **Real-time Chat** - WebSocket cho trải nghiệm real-time
🎫 **Product Cards** - Hiển thị sản phẩm đẹp mắt trong chat
🤖 **Fallback Support** - Vẫn hoạt động khi AI service offline
📱 **Responsive** - Tương thích mobile và desktop

---

## 🚀 Next Steps

1. Thêm authentication với JWT
2. Tích hợp với booking flow
3. Lưu lịch sử chat (optional)
4. Thêm typing indicators
5. Voice input support
6. Multi-language support

---

**Happy Coding! 🎉**

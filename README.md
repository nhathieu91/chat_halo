# Chatspace

MVP ứng dụng chat realtime: Spring Boot (Java 21), React + TypeScript (Vite), PostgreSQL. WebSocket dùng STOMP; chưa cần message queue cho giai đoạn MVP.

## Yêu cầu

- Java 21+
- Maven 3.9+
- Node.js 20+
- PostgreSQL 16+ (hoặc Docker Compose)

## Chạy PostgreSQL

Nếu có Docker:

```powershell
docker compose up -d postgres
```

Mặc định backend kết nối `jdbc:postgresql://localhost:5432/chatdb`, user/password `chat`.

## Chạy backend

```powershell
cd backend
mvn spring-boot:run
```

API chạy tại `http://localhost:8080`; WebSocket/STOMP endpoint là `ws://localhost:8080/ws`.
Healthcheck công khai tại `http://localhost:8080/actuator/health`, luôn trả `{"status":"UP"}` và không kiểm tra database hay yêu cầu đăng nhập.

## Chạy frontend

```powershell
cd frontend
npm install
npm run dev
```

Mở `http://localhost:5173`. Có thể cấu hình `VITE_API_URL` và `VITE_WS_URL` bằng biến môi trường khi frontend cần kết nối tới server khác.

## Tính năng hiện có

- Đăng ký, đăng nhập bằng JWT; mật khẩu được lưu dưới dạng BCrypt hash.
- Tạo phòng direct (hai thành viên) hoặc group.
- Mỗi phòng có UUID cố định; link dạng `http://localhost:5173/?room=<room-id>` mở trực tiếp phòng sau khi đăng nhập. API chỉ trả phòng cho thành viên; người khác nhận `404`.
- Gửi tin nhắn realtime qua STOMP/WebSocket và tải tối đa 100 tin nhắn gần nhất.
- Theo dõi danh sách user online trong bộ nhớ của backend.
- Chỉ thành viên phòng mới được subscribe và gửi tin nhắn vào phòng.
- Tin nhắn được mã hóa AES-256-GCM trước khi lưu vào PostgreSQL; backend giải mã khi trả lịch sử/phát realtime. Khóa được tạo tự động lần đầu và lưu trong bảng `app_secrets`.

Mã hóa này bảo vệ nội dung tin nhắn khi xem trực tiếp bảng `chat_messages`, không phải mã hóa đầu-cuối: khóa nằm trong cùng database, backend xử lý plaintext, và cần HTTPS/WSS để bảo vệ dữ liệu trên đường truyền. Không xóa hay sửa thủ công bản ghi khóa trong `app_secrets`; mất khóa đồng nghĩa không thể giải mã lịch sử.

## Lưu ý trước khi production

Đổi `JWT_SECRET`, cấu hình CORS theo domain thực tế, dùng schema migration (Flyway/Liquibase) thay `ddl-auto`, bật TLS và bổ sung quản lý refresh/revocation token. Presence hiện lưu trong RAM nên chỉ phù hợp chạy một backend instance.
# chat_halo

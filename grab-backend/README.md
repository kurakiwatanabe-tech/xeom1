# Grab-style Backend API

Backend Node.js + Express quản lý **khách hàng**, **tài xế**, và **tìm tài xế gần đây** theo vị trí (giống cơ chế tìm tài xế của Grab). Dữ liệu lưu trong file JSON đơn giản, không cần cài database ngoài — chạy được ngay.

## Cài đặt & chạy

```bash
npm install
npm start
```

Server chạy tại: `http://localhost:3000`

Muốn tự động reload khi sửa code: `npm run dev`

## Cấu trúc dự án

```
grab-backend/
├── server.js              # Điểm khởi động, gắn routes
├── db.js                  # Đọc/ghi dữ liệu JSON
├── data/db.json           # File lưu dữ liệu (có sẵn dữ liệu mẫu Hà Nội)
├── utils/distance.js      # Công thức Haversine tính khoảng cách
└── routes/
    ├── customers.js       # CRUD khách hàng
    └── drivers.js         # CRUD tài xế + tìm gần đây
```

## API — Khách hàng

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/customers` | Danh sách khách hàng (có thể lọc `?q=từ khóa`) |
| GET | `/api/customers/:id` | Chi tiết 1 khách hàng |
| POST | `/api/customers` | Tạo khách hàng mới |
| PUT | `/api/customers/:id` | Cập nhật thông tin |
| DELETE | `/api/customers/:id` | Xoá khách hàng |

Ví dụ tạo khách hàng:
```bash
curl -X POST http://localhost:3000/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Nguyễn Văn A","phone":"0901234567","email":"a@example.com"}'
```

## API — Tài xế

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/drivers` | Danh sách tài xế (lọc `?status=online&vehicle=bike`) |
| GET | `/api/drivers/:id` | Chi tiết 1 tài xế |
| POST | `/api/drivers` | Tạo tài xế mới |
| PUT | `/api/drivers/:id` | Cập nhật thông tin |
| PATCH | `/api/drivers/:id/location` | Cập nhật vị trí (lat, lng) |
| PATCH | `/api/drivers/:id/status` | Đổi trạng thái: `online` / `offline` / `busy` |
| DELETE | `/api/drivers/:id` | Xoá tài xế |

`vehicle` hợp lệ: `bike`, `car`, `car7`, `truck`

Ví dụ tạo tài xế:
```bash
curl -X POST http://localhost:3000/api/drivers \
  -H "Content-Type: application/json" \
  -d '{"name":"Lê Văn B","phone":"0912345678","vehicle":"bike","plate":"29A1-123.45","lat":21.03,"lng":105.85}'
```

Cập nhật vị trí (gọi liên tục khi tài xế di chuyển, ví dụ mỗi 5-10 giây từ app tài xế):
```bash
curl -X PATCH http://localhost:3000/api/drivers/drv_xxx/location \
  -H "Content-Type: application/json" \
  -d '{"lat":21.031,"lng":105.851}'
```

## API — Tìm tài xế gần đây ⭐

```
GET /api/drivers/nearby?lat=21.0285&lng=105.8542&radius_km=5&limit=10&vehicle=car&status=online
```

Tham số:
- `lat`, `lng` — **bắt buộc**, toạ độ điểm cần tìm (vị trí khách hàng)
- `radius_km` — bán kính tìm kiếm, mặc định 5km
- `limit` — số kết quả tối đa, mặc định 10
- `vehicle` — lọc theo loại xe (tuỳ chọn)
- `status` — mặc định `online`

Trả về danh sách tài xế **sắp xếp theo khoảng cách gần nhất**, kèm `distance_km`:

```json
{
  "origin": { "lat": 21.0285, "lng": 105.8542 },
  "radius_km": 5,
  "count": 2,
  "data": [
    { "id": "drv_seed002", "name": "Phạm Văn Dũng", "distance_km": 0.49, "...": "..." },
    { "id": "drv_seed001", "name": "Lê Văn Cường", "distance_km": 1.42, "...": "..." }
  ]
}
```

> Khoảng cách này tính theo đường chim bay (Haversine), không phải khoảng cách chạy xe thực tế. Nếu cần chính xác theo đường đi, sau khi lọc ra danh sách ứng viên gần nhất, gọi tiếp OSRM/OpenRouteService cho từng tài xế để lấy quãng đường + thời gian thực tế.

## Nâng cấp khi lên production

- Thay file JSON bằng **PostgreSQL + PostGIS** (`ST_DWithin`, `ST_Distance`) hoặc **MongoDB** (geospatial index `2dsphere`) để truy vấn "gần đây" nhanh với lượng tài xế lớn.
- Thêm xác thực (JWT) cho các endpoint tạo/sửa/xoá.
- Dùng WebSocket (Socket.IO) để đẩy vị trí tài xế realtime tới khách hàng thay vì polling.
- Validate số điện thoại/email kỹ hơn, thêm rate limiting.

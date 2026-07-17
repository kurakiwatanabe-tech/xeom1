1. Chạy Redis bằng Docker
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:latest

Kiểm tra:

docker ps
2. Vào Redis CLI
docker exec -it redis redis-cli

Nếu có mật khẩu:

redis-cli -a your_password
3. Thêm vị trí (GEOADD)

Ví dụ lưu vị trí tài xế.

GEOADD drivers 105.8342 21.0278 driver1
GEOADD drivers 105.8400 21.0300 driver2
GEOADD drivers 105.8500 21.0200 driver3

Hoặc thêm nhiều phần tử:

GEOADD drivers \
105.8342 21.0278 driver1 \
105.8400 21.0300 driver2 \
105.8500 21.0200 driver3
4. Lấy tọa độ (GEOPOS)
GEOPOS drivers driver1

Kết quả:

1) 1) "105.8342"
   2) "21.0278"
5. Khoảng cách giữa hai điểm (GEODIST)
GEODIST drivers driver1 driver2 km

hoặc

GEODIST drivers driver1 driver2 m

Đơn vị:

m
km
mi
ft
6. Tìm người gần nhất (GEOSEARCH)

Ví dụ tìm tài xế trong bán kính 5 km.

GEOSEARCH drivers
FROMMEMBER driver1
BYRADIUS 5 km

Hoặc theo tọa độ:

GEOSEARCH drivers
FROMLONLAT 105.8350 21.0280
BYRADIUS 3 km
7. Trả về khoảng cách
GEOSEARCH drivers
FROMMEMBER driver1
BYRADIUS 10 km
WITHDIST

Ví dụ:

driver1 0
driver2 1.2
driver3 2.8
8. Trả về tọa độ
GEOSEARCH drivers
FROMMEMBER driver1
BYRADIUS 10 km
WITHCOORD
9. Trả về tất cả thông tin
GEOSEARCH drivers
FROMMEMBER driver1
BYRADIUS 10 km
WITHDIST
WITHCOORD
10. Tìm theo hình chữ nhật
GEOSEARCH drivers
FROMLONLAT 105.8350 21.0280
BYBOX 10 5 km
11. Lưu kết quả
GEOSEARCHSTORE nearby
drivers
FROMMEMBER driver1
BYRADIUS 5 km

Kiểm tra:

ZRANGE nearby 0 -1
12. Xóa tài xế

Redis GEO được lưu bằng Sorted Set, nên dùng:

ZREM drivers driver2
13. Đếm số tài xế
ZCARD drivers
14. Xóa toàn bộ
DEL drivers
15. Xem tất cả
ZRANGE drivers 0 -1
Redis GEO được lưu như thế nào?

Redis không có cấu trúc dữ liệu GEO riêng. Dữ liệu được lưu dưới dạng Sorted Set (ZSET), trong đó:

Member: tên đối tượng (ví dụ driver1)
Score: giá trị geohash được mã hóa từ kinh độ và vĩ độ

Ví dụ:

drivers
├── driver1
├── driver2
└── driver3

Khi gọi GEOSEARCH, Redis sử dụng geohash để thu hẹp vùng tìm kiếm, sau đó tính khoảng cách thực tế giữa các điểm để trả về kết quả chính xác.

Ví dụ cho ứng dụng đặt xe
GEOADD drivers 105.8342 21.0278 driver_001
GEOADD drivers 105.8360 21.0285 driver_002
GEOADD drivers 105.8405 21.0301 driver_003

Khách hàng ở:

105.8350
21.0280

Tìm tài xế trong phạm vi 2 km:

GEOSEARCH drivers
FROMLONLAT 105.8350 21.0280
BYRADIUS 2 km
WITHDIST
ASC
COUNT 5

Kết quả sẽ trả về tối đa 5 tài xế gần nhất, sắp xếp theo khoảng cách tăng dần. Đây cũng là cách các ứng dụng như Grab, Be hay Gojek thường dùng Redis GEO để tìm nhanh danh sách tài xế gần khách, trước khi áp dụng thêm các điều kiện nghiệp vụ khác như trạng thái tài xế, loại xe hoặc mức ưu tiên.
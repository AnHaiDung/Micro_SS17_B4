# DỰ ÁN BÀI TẬP 4: CHỐNG CACHE STAMPEDE VỚI THUỘC TÍNH SYNC = TRUE

Ứng dụng Spring Boot tối ưu hóa bộ nhớ đệm và xử lý truy xuất đồng thời cao, phòng chống hiện tượng **Cache Stampede** trong các sự kiện Flash Sale bằng cơ chế khóa nội tại `sync = true` của `@Cacheable`.

---

## 📁 CẤU TRÚC DỰ ÁN

```text
SS17_B4/
├── build.gradle                               # Gradle build script (Spring Boot 3.3.4, Caffeine, Web, Lombok)
├── settings.gradle                            # Tên dự án: SS17_B4
├── Dockerfile                                 # Multi-stage Docker containerization
├── docker-compose.yml                         # Cấu hình container cho App + Redis Cache
├── flash_sale_concurrent_test.jmx             # Kịch bản kiểm thử tải Apache JMeter (50 Concurrent Threads)
├── LOG_REPORT.md                              # Báo cáo thực nghiệm & bằng chứng log chi tiết theo barem
├── src/
│   ├── main/
│   │   ├── java/com/demo/
│   │   │   ├── Application.java               # Main class với @EnableCaching
│   │   │   ├── config/
│   │   │   │   └── CacheConfig.java           # Cấu hình Caffeine Cache Manager với per-key locking
│   │   │   ├── model/
│   │   │   │   └── Product.java               # POJO Product model
│   │   │   ├── service/
│   │   │   │   ├── ProductService.java        # Interface service
│   │   │   │   └── impl/
│   │   │   │       └── ProductServiceImpl.java# Chứa @Cacheable(value = "flash-sale", key = "#id", sync = true)
│   │   │   └── controller/
│   │   │       └── ProductController.java     # REST API & Endpoint kích hoạt 50 CompletableFuture threads
│   │   └── resources/
│   │       └── application.yml                # Cấu hình cổng 8080 & logging pattern
│   └── test/
│       └── java/com/demo/
│           └── ConcurrentCacheTest.java       # Bộ test tự động gửi 50 request đồng thời kiểm chứng DB hit
```

---

## 🚀 HƯỚNG DẪN CHẠY DỰ ÁN

### Cách 1: Chạy trực tiếp qua Gradle Wrapper

```bash
# 1. Chạy toàn bộ bài test kiểm chứng 50 luồng đồng thời (sẽ in kết quả và log)
.\gradlew.bat test

# 2. Khởi chạy ứng dụng Web (port 8080)
.\gradlew.bat bootRun
```

### Cách 2: Chạy qua Docker Compose

```bash
# Khởi động cả ứng dụng và Redis container
docker-compose up -d --build

# Xem log của ứng dụng
docker-compose logs -f app

# Dừng container
docker-compose down
```

---

## 📡 CÁC ENDPOINT TEST TRỰC TIẾP

| Phương thức | Đường dẫn | Chức năng |
|:---|:---|:---|
| `GET` | `/api/products/1` | Lấy chi tiết sản phẩm (áp dụng `@Cacheable(sync = true)`) |
| `POST` | `/api/products/test/concurrent-sync/1?totalRequests=50` | **Kích hoạt tự động 50 luồng đồng thời** với `sync = true`, trả về JSON báo cáo số lần gọi DB và thời gian thực thi |
| `DELETE` | `/api/products/cache` | Xóa sạch cache `flash-sale` để test lại từ đầu |

### Ví dụ kết quả trả về từ endpoint kiểm thử:
```json
{
  "mode": "sync = true (Cache Stampede Protection)",
  "productId": 1,
  "totalRequests": 50,
  "successfulRequests": 50,
  "databaseHits": 1,
  "totalExecutionTimeMs": 2052,
  "isStampedePrevented": true,
  "message": "SUCCESS: Only 1 database query executed! 49 other threads safely fetched from cache."
}
```

---

## 📋 ĐỐI CHIẾU YÊU CẦU BÀI TẬP

| Tiêu chí | Trạng thái | Chi tiết |
|:---|:---:|:---|
| `@Cacheable(value = "flash-sale", key = "#id", sync = true)` | ✅ ĐẠT | Khai báo chính xác tại [ProductServiceImpl.java](file:///c:/Microservice/SS17_B4/src/main/java/com/demo/service/impl/ProductServiceImpl.java#L27) |
| Log `Fetching from Database for product {}` | ✅ ĐẠT | Xuất hiện duy nhất 1 lần khi có 50 request đồng thời |
| `Thread.sleep(2000)` mô phỏng truy vấn phức tạp | ✅ ĐẠT | Tái tạo chính xác độ trễ của DB |
| Bằng chứng Log thay cho ảnh chụp | ✅ ĐẠT | Được lưu chi tiết tại [LOG_REPORT.md](file:///c:/Microservice/SS17_B4/LOG_REPORT.md) |
| `build.gradle` & `docker-compose.yml` | ✅ ĐẠT | Đầy đủ, build và chạy độc lập |

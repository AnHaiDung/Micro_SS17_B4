package com.demo.service.impl;

import com.demo.model.Product;
import com.demo.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private final AtomicInteger dbHitCounter = new AtomicInteger(0);
    private final AtomicInteger noSyncDbHitCounter = new AtomicInteger(0);

    /**
     * Bước 1: Đánh dấu @Cacheable với sync = true để ngăn chặn Cache Stampede.
     * Khi có 50 request cùng gọi vào phương thức này khi cache rỗng:
     * - Chỉ 1 thread duy nhất được phép thực thi logic bên trong (xuống DB).
     * - 49 thread còn lại bị block và đợi thread đầu tiên tính toán xong.
     * - Sau khi thread đầu tiên hoàn thành và nạp cache, 49 thread kia lấy trực tiếp từ cache mà không xuống DB.
     */
    @Override
    @Cacheable(value = "flash-sale", key = "#id", sync = true)
    public Product getProductById(Long id) {
        int currentHits = dbHitCounter.incrementAndGet();
        
        // Bước 2: Log theo dõi số lần truy xuất DB
        log.info("Fetching from Database for product {}", id);

        try {
            // Bước 3: Giả lập thời gian tái tạo cache bằng Thread.sleep(2000) (mô phỏng query phức tạp)
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during DB simulation for product {}", id, e);
        }

        return Product.builder()
                .id(id)
                .name("Flash Sale iPhone 16 Pro Max - Deal 1k")
                .category("Electronics")
                .price(BigDecimal.valueOf(29990000))
                .stock(50)
                .description("Sản phẩm Flash Sale giới hạn trong khung giờ vàng")
                .build();
    }

    /**
     * Phương thức đối chứng: Không có sync = true (sync = false mặc định).
     * Dùng để kiểm chứng hiện tượng Cache Stampede khi 50 request đổ vào cùng lúc.
     */
    @Override
    @Cacheable(value = "flash-sale-nosync", key = "#id", sync = false)
    public Product getProductByIdWithoutSync(Long id) {
        noSyncDbHitCounter.incrementAndGet();
        log.warn("[NO-SYNC / CACHE STAMPEDE] Fetching from Database for product {}", id);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during DB simulation", e);
        }

        return Product.builder()
                .id(id)
                .name("Flash Sale iPhone 16 Pro Max - No Sync")
                .category("Electronics")
                .price(BigDecimal.valueOf(29990000))
                .stock(50)
                .description("Sản phẩm thử nghiệm Cache Stampede (không dùng sync)")
                .build();
    }

    @Override
    public int getDbHitCount() {
        return dbHitCounter.get();
    }

    @Override
    public void resetDbHitCount() {
        dbHitCounter.set(0);
        noSyncDbHitCounter.set(0);
    }

    @Override
    @CacheEvict(value = {"flash-sale", "flash-sale-nosync"}, allEntries = true)
    public void clearCache() {
        log.info("Cleared all entries in flash-sale and flash-sale-nosync caches");
    }
}

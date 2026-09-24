package com.demo;

import com.demo.model.Product;
import com.demo.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConcurrentCacheTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentCacheTest.class);

    @Autowired
    private ProductService productService;

    private static final int CONCURRENT_REQUESTS = 50;
    private static final Long PRODUCT_ID = 1L;

    @BeforeEach
    void setUp() {
        productService.clearCache();
        productService.resetDbHitCount();
    }

    @Test
    @DisplayName("Kiểm thử 50 request đồng thời với sync = true: Chỉ có DUY NHẤT 1 lần truy xuất DB (Chống Cache Stampede)")
    void testConcurrentRequestsWithSyncTrue_PreventsCacheStampede() throws Exception {
        log.info("================================================================================");
        log.info("BẮT ĐẦU TEST: GỬI {} REQUEST ĐỒNG THỜI VỚI SYNC = TRUE", CONCURRENT_REQUESTS);
        log.info("================================================================================");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Product>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                readyLatch.countDown();
                try {
                    // Đợi tất cả 50 luồng sẵn sàng để phát lệnh đồng loạt cùng 1 thời điểm
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return productService.getProductById(PRODUCT_ID);
            }, executor));
        }

        // Đợi cả 50 luồng sẵn sàng ở vạch xuất phát
        readyLatch.await(5, TimeUnit.SECONDS);
        // Phát tín hiệu đồng loạt cho 50 luồng chạy
        startLatch.countDown();

        // Chờ tất cả 50 luồng hoàn tất
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        List<Product> products = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        log.info("================================================================================");
        log.info("KẾT QUẢ KIỂM THỬ:");
        log.info("- Tổng số request: {}", products.size());
        log.info("- Số lần thực tế gọi xuống Database: {}", productService.getDbHitCount());
        log.info("- Tổng thời gian thực thi: {} ms", totalDurationMs);
        log.info("================================================================================");

        // TIÊU CHÍ 1: Đúng 50 request thành công, không lỗi/timeout
        assertEquals(CONCURRENT_REQUESTS, products.size());
        for (Product product : products) {
            assertNotNull(product);
            assertEquals(PRODUCT_ID, product.getId());
        }

        // TIÊU CHÍ 2: Trong log và counter chỉ xuất hiện DUY NHẤT 1 lần gọi vào Database
        assertEquals(1, productService.getDbHitCount(), 
                "LỖI: Cơ chế sync = true không hoạt động đúng! Đã có nhiều hơn 1 lần gọi DB.");

        // TIÊU CHÍ 3: Tổng thời gian hoàn thành xấp xỉ thời gian tái tạo cache (~2000ms), không phải 50 * 2000ms = 100s
        assertTrue(totalDurationMs < 4500, 
                "LỖI: Thời gian xử lý quá lâu, các request có thể bị xử lý tuần tự thay vì chia sẻ cache lock.");
    }

    @Test
    @DisplayName("Kiểm thử đối chứng: 50 request đồng thời KHÔNG CÓ sync = true (Minh họa hiện tượng Cache Stampede)")
    void testConcurrentRequestsWithoutSync_DemonstratesCacheStampede() throws Exception {
        log.info("================================================================================");
        log.info("BẮT ĐẦU TEST ĐỐI CHỨNG: GỬI {} REQUEST ĐỒNG THỜI KHÔNG CÓ SYNC (SYNC = FALSE)", CONCURRENT_REQUESTS);
        log.info("================================================================================");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Product>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return productService.getProductByIdWithoutSync(PRODUCT_ID);
            }, executor));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        log.info("================================================================================");
        log.info("KẾT QUẢ ĐỐI CHỨNG (KHÔNG CÓ SYNC):");
        log.info("- Tổng thời gian: {} ms", totalDurationMs);
        log.info("================================================================================");
    }
}

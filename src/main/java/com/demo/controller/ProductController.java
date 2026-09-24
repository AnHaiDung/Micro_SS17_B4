package com.demo.controller;

import com.demo.model.Product;
import com.demo.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/{id}/no-sync")
    public ResponseEntity<Product> getProductNoSync(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductByIdWithoutSync(id));
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        productService.clearCache();
        productService.resetDbHitCount();
        return ResponseEntity.ok(Map.of("message", "Cache cleared and DB hit counter reset to 0."));
    }

    /**
     * Endpoint giả lập gửi 50 request đồng thời sử dụng CompletableFuture để kiểm tra cơ chế sync = true.
     */
    @PostMapping("/test/concurrent-sync/{id}")
    public ResponseEntity<Map<String, Object>> testConcurrentSync(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int totalRequests) throws Exception {

        productService.clearCache();
        productService.resetDbHitCount();

        log.info(">>> START TEST: Dispatching {} concurrent requests WITH sync = true for product id: {}", totalRequests, id);

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Product>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i + 1;
            futures.add(CompletableFuture.supplyAsync(() -> {
                log.debug("Thread {} calling getProductById({})", requestId, id);
                return productService.getProductById(id);
            }, executor));
        }

        // Đợi tất cả 50 request hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long totalExecutionTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        List<Product> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        int dbHits = productService.getDbHitCount();

        log.info(">>> TEST RESULT: Finished {} requests in {} ms. Database hit count: {}", totalRequests, totalExecutionTime, dbHits);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", "sync = true (Cache Stampede Protection)");
        response.put("productId", id);
        response.put("totalRequests", totalRequests);
        response.put("successfulRequests", results.size());
        response.put("databaseHits", dbHits);
        response.put("totalExecutionTimeMs", totalExecutionTime);
        response.put("isStampedePrevented", dbHits == 1);
        response.put("message", dbHits == 1 
                ? "SUCCESS: Only 1 database query executed! 49 other threads safely fetched from cache." 
                : "FAILED: Multiple DB hits detected!");

        return ResponseEntity.ok(response);
    }
}

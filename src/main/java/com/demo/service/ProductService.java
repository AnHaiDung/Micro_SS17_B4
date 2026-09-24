package com.demo.service;

import com.demo.model.Product;

public interface ProductService {
    Product getProductById(Long id);
    Product getProductByIdWithoutSync(Long id);
    int getDbHitCount();
    void resetDbHitCount();
    void clearCache();
}

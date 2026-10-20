package com.acme.oims.catalog;

import java.math.BigDecimal;

public record ProductView(Long id, String sku, String name, BigDecimal price) {

    public static ProductView from (Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getPrice());
    }
}

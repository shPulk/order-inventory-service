package com.acme.oims.order;

import com.acme.oims.common.BusinessRuleException;

import java.util.List;

public class InsufficientStockException extends BusinessRuleException {

    private final Long orderId;
    private final List<Long> productIds;

    public InsufficientStockException(Long orderId, List<Long> productIds) {
        super("Insufficient stock for product ids" + productIds +
                ". Order" + orderId + "recorded as FAILED");
        this.orderId = orderId;
        this.productIds = productIds;
    }

    public Long getOrderId() {
        return orderId;
    }

    public List<Long> getProductIds() {
        return productIds;
    }
}

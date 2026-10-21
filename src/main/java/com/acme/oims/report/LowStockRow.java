package com.acme.oims.report;

public record LowStockRow(String warehouseCode, String warehouseName, Long productId, String sku,
                          String productName, int onHand, int reserved, int available) {}
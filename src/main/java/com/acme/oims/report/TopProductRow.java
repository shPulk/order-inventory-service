package com.acme.oims.report;

import java.math.BigDecimal;

public record TopProductRow(Long productId, String sku, String name, long units, BigDecimal revenue, long orders) {}
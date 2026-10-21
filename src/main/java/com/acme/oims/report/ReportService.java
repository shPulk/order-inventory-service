package com.acme.oims.report;

import com.acme.oims.common.BusinessRuleException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;


@Service
public class ReportService {

    private static final int MAX_LIMIT = 100;

    private final NamedParameterJdbcTemplate jdbc;

    public ReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TopProductRow> topProducts(Instant from, Instant to, int limit) {
        Instant end = to != null
                ? to
                : Instant.now().plus(Duration.ofDays(1));

        Instant start = from != null
                ? from
                : end.minus(Duration.ofDays(30));

        if (!start.isBefore(end)) {
            throw new BusinessRuleException("'from' must be before 'to'");
        }

        String sql = """
                SELECT p.id, p.sku, p.name,
                       SUM(l.quantity) AS units,
                       SUM(l.quantity * l.unit_price) AS revenue,
                       COUNT(DISTINCT o.id) AS orders
                FROM order_line l
                JOIN orders o ON o.id = l.order_id
                JOIN product p ON p.id = l.product_id
                WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
                  AND o.created_at >= :from
                  AND o.created_at < :to
                GROUP BY p.id, p.sku, p.name
                ORDER BY units DESC, p.id
                LIMIT :limit
                """;

        var params = new MapSqlParameterSource()
                .addValue(
                        "from",
                        OffsetDateTime.ofInstant(start, ZoneOffset.UTC)
                )
                .addValue(
                        "to",
                        OffsetDateTime.ofInstant(end, ZoneOffset.UTC)
                )
                .addValue(
                        "limit",
                        clamp(limit)
                );

        return jdbc.query(
                sql,
                params,
                (rs, i) -> new TopProductRow(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getLong("units"),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("orders")
                )
        );
    }

    @Transactional(readOnly = true)
    public List<LowStockRow> lowStock(int threshold, Long warehouseId, int limit) {
        if (threshold < 0) {
            throw new BusinessRuleException("threshold must be >= 0");
        }

        StringBuilder sql = new StringBuilder("""
            SELECT w.code AS wh_code,
                   w.name AS wh_name,
                   p.id AS product_id,
                   p.sku,
                   p.name AS product_name,
                   s.on_hand,
                   s.reserved,
                   (s.on_hand - s.reserved) AS available
            FROM stock_level s
            JOIN warehouse w ON w.id = s.warehouse_id
            JOIN product p ON p.id = s.product_id
            WHERE (s.on_hand - s.reserved) <= :threshold
            """);

        var params = new MapSqlParameterSource()
                .addValue("threshold", threshold)
                .addValue("limit", clamp(limit));

        if (warehouseId != null) {
            sql.append(" AND w.id = :warehouseId\n");
            params.addValue("warehouseId", warehouseId);
        }

        sql.append(" ORDER BY available ASC, w.code, p.sku\n LIMIT :limit");

        return jdbc.query(
                sql.toString(),
                params,
                (rs, i) -> new LowStockRow(
                        rs.getString("wh_code"),
                        rs.getString("wh_name"),
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getInt("on_hand"),
                        rs.getInt("reserved"),
                        rs.getInt("available")
                )
        );
    }

    private static int clamp(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}

INSERT INTO product (sku, name, price)
SELECT s.sku, s.name, s.price
FROM (VALUES
          ('SKU-001', 'Widget', 9.99),
          ('SKU-002', 'Gadget', 24.50),
          ('SKU-003', 'Gizmo', 99.00)
     ) AS s(sku, name, price)
WHERE NOT EXISTS (
    SELECT 1
    FROM product p
    WHERE p.sku = s.sku
);

INSERT INTO warehouse (code, name)
SELECT s.code, s.name
FROM (VALUES
          ('WH-EAST', 'East Coast DC'),
          ('WH-WEST', 'West Coast DC')
     ) AS s(code, name)
WHERE NOT EXISTS (
    SELECT 1
    FROM warehouse w
    WHERE w.code = s.code
);

INSERT INTO stock_level (warehouse_id, product_id, on_hand, reserved)
SELECT w.id, p.id, s.qty, 0
FROM (VALUES
          ('WH-EAST', 'SKU-001', 100),
          ('WH-WEST', 'SKU-001', 40),
          ('WH-EAST', 'SKU-002', 10),
          ('WH-WEST', 'SKU-002', 25),
          ('WH-EAST', 'SKU-003', 5)
     ) AS s(wh, sku, qty)
         JOIN warehouse w ON w.code = s.wh
         JOIN product p ON p.sku = s.sku
WHERE NOT EXISTS (
    SELECT 1
    FROM stock_level x
    WHERE x.warehouse_id = w.id
      AND x.product_id = p.id
);
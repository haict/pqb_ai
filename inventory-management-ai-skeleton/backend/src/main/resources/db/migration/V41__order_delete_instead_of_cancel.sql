-- D-65 (2026-10-29): đơn mua / đơn bán bỏ trạng thái CANCELLED; đơn chưa từng ghi sổ được xóa hẳn (xác nhận, lý do,
-- audit). Đơn đã từng ghi sổ rồi bỏ ghi sổ (D-62) còn sổ phụ tham chiếu nên không xóa: đánh dấu bằng ever_posted.

ALTER TABLE purchase_orders ADD COLUMN ever_posted BOOLEAN NOT NULL DEFAULT FALSE AFTER posted_at;
ALTER TABLE sales_orders ADD COLUMN ever_posted BOOLEAN NOT NULL DEFAULT FALSE AFTER posted_at;

UPDATE purchase_orders o
SET o.ever_posted = TRUE
WHERE o.status IN ('POSTED', 'REVERSED')
   OR EXISTS (SELECT 1 FROM inventory_documents d WHERE d.source_type = 'DMH' AND d.source_id = o.id)
   OR EXISTS (SELECT 1 FROM debt_items i WHERE i.source_type = 'DMH' AND i.source_id = o.id)
   OR EXISTS (SELECT 1 FROM journal_entries e WHERE e.source_type = 'DMH' AND e.source_id = o.id);

UPDATE sales_orders o
SET o.ever_posted = TRUE
WHERE o.status IN ('POSTED', 'REVERSED')
   OR EXISTS (SELECT 1 FROM inventory_documents d WHERE d.source_type = 'DBH' AND d.source_id = o.id)
   OR EXISTS (SELECT 1 FROM debt_items i WHERE i.source_type = 'DBH' AND i.source_id = o.id)
   OR EXISTS (SELECT 1 FROM journal_entries e WHERE e.source_type = 'DBH' AND e.source_id = o.id);

-- Đơn đã hủy chưa từng ghi sổ: xóa hẳn cùng dòng (không còn trạng thái CANCELLED cho đơn).
DELETE s FROM purchase_order_serials s
    JOIN purchase_order_lines l ON l.id = s.line_id
    JOIN purchase_orders o ON o.id = l.order_id
WHERE o.status = 'CANCELLED';
DELETE l FROM purchase_order_lines l JOIN purchase_orders o ON o.id = l.order_id WHERE o.status = 'CANCELLED';
DELETE FROM purchase_orders WHERE status = 'CANCELLED';

DELETE s FROM sales_order_serials s
    JOIN sales_order_lines l ON l.id = s.line_id
    JOIN sales_orders o ON o.id = l.order_id
WHERE o.status = 'CANCELLED';
DELETE i FROM sales_order_items i
    JOIN sales_order_lines l ON l.id = i.line_id
    JOIN sales_orders o ON o.id = l.order_id
WHERE o.status = 'CANCELLED';
DELETE l FROM sales_order_lines l JOIN sales_orders o ON o.id = l.order_id WHERE o.status = 'CANCELLED';
DELETE t FROM sales_order_payment_terms t JOIN sales_orders o ON o.id = t.order_id WHERE o.status = 'CANCELLED';
DELETE FROM sales_orders WHERE status = 'CANCELLED';

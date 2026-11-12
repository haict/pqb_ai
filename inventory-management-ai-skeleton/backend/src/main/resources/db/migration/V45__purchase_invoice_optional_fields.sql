-- D-75: hóa đơn của nhà cung cấp trên đơn mua không bắt buộc đủ ký hiệu, số, ngày; nhập trường nào lưu trường đó.
-- Có số hóa đơn thì vẫn cần MST người bán để kiểm tra trùng (company_id, duplicate_key).
ALTER TABLE purchase_orders DROP CHECK ck_purchase_orders_invoice;
ALTER TABLE purchase_orders ADD CONSTRAINT ck_purchase_orders_invoice
    CHECK (invoice_no IS NULL OR seller_tax_code IS NOT NULL);

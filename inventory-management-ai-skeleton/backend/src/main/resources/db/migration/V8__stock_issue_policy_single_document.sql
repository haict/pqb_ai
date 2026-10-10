-- D-53 (chốt 2026-10-10, theo D-40 "Giám đốc duyệt theo từng chứng từ"): mỗi chính sách xuất vượt tồn khả dụng
-- chỉ dùng cho một chứng từ xuất. Ghi chứng từ đã dùng để chặn chứng từ thứ hai và truy vết hai chiều.
ALTER TABLE stock_issue_policies
    ADD COLUMN used_by_document_id BIGINT NULL AFTER used_quantity,
    ADD CONSTRAINT fk_stock_issue_policies_documents FOREIGN KEY (used_by_document_id)
        REFERENCES inventory_documents (id);

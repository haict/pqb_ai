-- D-61 (2026-10-21), đợt 4 - kho: chứng từ kho, kiểm kê không qua duyệt (Nháp / Đang đếm -> Ghi sổ); thu hồi và chính
-- sách xuất vượt tồn khả dụng kích hoạt thẳng từ nháp bằng quyền riêng. Lịch sử duyệt cũ giữ nguyên để tra cứu; chứng
-- từ cũ đang chờ duyệt / bị từ chối chỉ còn hủy, đã duyệt vẫn ghi sổ được.

UPDATE sys_document_types SET approval_mode = 'NONE'
WHERE code IN ('PNK', 'PXK', 'PXH', 'PCK', 'PDC', 'PCL', 'PDK', 'PKK', 'PTH', 'CSX');
UPDATE approval_workflows SET status = 'INACTIVE'
WHERE document_type IN ('PNK', 'PXK', 'PXH', 'PCK', 'PDC', 'PCL', 'PDK', 'PKK', 'PTH', 'CSX');

-- Thu hồi (BR-INV-42) và chính sách xuất vượt tồn khả dụng (BR-GEN-31, D-10, D-40, D-53): quyền duyệt cũ đổi thành
-- quyền kích hoạt, giữ nguyên vai trò đang có (mặc định Giám đốc, Giám đốc chi nhánh).
UPDATE sys_permissions SET code = 'recall:manage', name = 'Kích hoạt, đóng thu hồi'
WHERE code = 'recall:approve';
UPDATE sys_permissions SET code = 'stock-policy:activate', name = 'Kích hoạt chính sách xuất vượt tồn khả dụng'
WHERE code = 'stock-policy:approve';

DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code IN ('inventory-receipt:approve', 'inventory-issue:approve', 'inventory-transfer:approve',
                 'inventory-adjustment:approve', 'inventory-reversal:approve', 'stock-count:approve');
DELETE FROM sys_permissions
WHERE code IN ('inventory-receipt:approve', 'inventory-issue:approve', 'inventory-transfer:approve',
               'inventory-adjustment:approve', 'inventory-reversal:approve', 'stock-count:approve');

UPDATE sys_permissions SET name = REPLACE(name, 'gửi duyệt, ', '')
WHERE code IN ('inventory-receipt:create', 'inventory-issue:create', 'inventory-transfer:create');
UPDATE sys_permissions SET name = 'Lập, bắt đầu, nhập số đếm kiểm kê' WHERE code = 'stock-count:create';
UPDATE sys_permissions SET name = 'Lập thu hồi' WHERE code = 'recall:create';

-- D-61 (2026-10-21), đợt 5 - hệ thống: bỏ cấu hình luồng duyệt, ủy quyền duyệt, chế độ duyệt theo loại chứng từ và
-- hộp việc chờ duyệt. Bảng approval_workflows, approval_workflow_steps, approval_delegations, approval_requests,
-- approval_request_steps, approval_actions và cột sys_document_types.approval_mode / self_approval_allowed giữ nguyên
-- để tra cứu lịch sử (không xóa dữ liệu đã phát sinh); ứng dụng không ghi các bảng cấu hình này nữa.

UPDATE sys_document_types SET approval_mode = 'NONE' WHERE approval_mode <> 'NONE';
UPDATE approval_workflows SET status = 'INACTIVE' WHERE status <> 'INACTIVE';

DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code IN ('approval-workflow:view', 'approval-workflow:manage') OR p.code LIKE '%:approve';
DELETE FROM sys_permissions
WHERE code IN ('approval-workflow:view', 'approval-workflow:manage') OR code LIKE '%:approve';

package com.company.erp.system.application;

/**
 * Quyền đặc biệt cho phép gán vai trò / quyền vượt quá quyền của chính người gán. Quản trị hệ thống cần quyền này để
 * cấu hình vai trò nghiệp vụ (Giám đốc, Kế toán trưởng...) mà bản thân không được dùng (ví dụ mở lại kỳ, BR-SYS-32).
 * Không ai tự thay đổi quyền của chính mình (UserAdminService), mọi thay đổi đều ghi audit.
 */
final class AccessGrants {

    static final String GRANT_ANY_PERMISSION = "access:grant-any";

    private AccessGrants() {
    }
}

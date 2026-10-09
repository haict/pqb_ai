package com.company.erp.system.application;

/** Mã hành động và loại thực thể ghi vào nhật ký audit của module system. */
final class AuditActions {

    static final String CREATE = "CREATE";
    static final String UPDATE = "UPDATE";
    static final String CHANGE_STATUS = "CHANGE_STATUS";
    static final String RESET_PASSWORD = "RESET_PASSWORD";
    static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    static final String ASSIGN_ROLES = "ASSIGN_ROLES";
    static final String ASSIGN_SCOPES = "ASSIGN_SCOPES";
    static final String ASSIGN_PERMISSIONS = "ASSIGN_PERMISSIONS";

    static final String COMPANY = "COMPANY";
    static final String BRANCH = "BRANCH";
    static final String DEPARTMENT = "DEPARTMENT";
    static final String USER = "USER";
    static final String ROLE = "ROLE";

    private AuditActions() {
    }
}

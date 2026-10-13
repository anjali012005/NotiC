package io.github.anjali.notifyflow.management.security;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void setCurrentTenantId(UUID tenantId) {
        if (tenantId == null) {
            CURRENT_TENANT_ID.remove();
            return;
        }
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}

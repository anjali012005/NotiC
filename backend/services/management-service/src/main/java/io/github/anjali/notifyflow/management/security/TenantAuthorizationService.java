package io.github.anjali.notifyflow.management.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TenantAuthorizationService {

    public UUID currentTenantId() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal.getTenantId();
        }

        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    public void requireTenantAccess(UUID tenantId) {
        if (tenantId == null) {
            throw new AccessDeniedException("Resource is not associated with a tenant");
        }

        UUID current = currentTenantId();
        if (!current.equals(tenantId)) {
            throw new AccessDeniedException("Tenant does not have access to the requested resource");
        }
    }

    public UUID requireCurrentTenant() {
        UUID tenantId = currentTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant is associated with the current request");
        }
        return tenantId;
    }
}

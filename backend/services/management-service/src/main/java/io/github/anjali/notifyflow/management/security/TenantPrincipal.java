package io.github.anjali.notifyflow.management.security;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class TenantPrincipal implements Principal {

    private final String apiKeyId;
    private final UUID tenantId;
    private final String role;

    public TenantPrincipal(String apiKeyId, UUID tenantId, String role) {
        this.apiKeyId = apiKeyId;
        this.tenantId = tenantId;
        this.role = role == null || role.isBlank() ? "TENANT" : role;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getRole() {
        return role;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return apiKeyId;
    }
}

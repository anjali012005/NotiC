package io.github.anjali.notifyflow.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import jakarta.servlet.http.HttpServletResponse;

class ApiKeySecurityTest {

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        properties.setApiKeys(java.util.Map.of(
                "tenant-a", "tenant-a-secret",
                "tenant-b", "tenant-b-secret"));
        filter = new ApiKeyAuthenticationFilter(properties);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void healthEndpointPassesWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void invalidApiKeyIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("Invalid API keys must not reach the request chain");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validApiKeyIsAccepted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader("X-API-Key", "tenant-a-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID expectedTenant = UUID.nameUUIDFromBytes("tenant-a".getBytes());

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(expectedTenant);
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(TenantContext.getCurrentTenantId()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void mismatchedTenantHeaderIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader("X-API-Key", "tenant-a-secret");
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("A mismatched tenant must not reach the request chain");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void tenantContextAcceptsMatchingTenant() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TenantContext.setCurrentTenantId(tenantId);
        try {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(tenantId);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void tenantAuthorizationRejectsTenantlessResources() {
        TenantAuthorizationService authorizationService = new TenantAuthorizationService();

        assertThatThrownBy(() -> authorizationService.requireTenantAccess(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void tenantAuthorizationRejectsResourcesOwnedByAnotherTenant() {
        UUID currentTenant = UUID.randomUUID();
        TenantContext.setCurrentTenantId(currentTenant);

        try {
            assertThatThrownBy(() -> new TenantAuthorizationService().requireTenantAccess(UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            TenantContext.clear();
        }
    }
}

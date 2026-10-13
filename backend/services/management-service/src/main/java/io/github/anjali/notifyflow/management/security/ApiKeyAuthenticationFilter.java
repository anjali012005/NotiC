package io.github.anjali.notifyflow.management.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;

    public ApiKeyAuthenticationFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!securityProperties.isEnabled()) {
            return true;
        }

        String requestUri = request.getRequestURI();
        return requestUri.startsWith("/actuator/health") || requestUri.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API key is required");
            return;
        }

        TenantPrincipal principal = securityProperties.resolvePrincipal(apiKey)
                .orElse(null);
        if (principal == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                UUID requestedTenantId = UUID.fromString(tenantHeader);
                if (!requestedTenantId.equals(principal.getTenantId())) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
                    return;
                }
            } catch (IllegalArgumentException ex) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant ID is not valid");
                return;
            }
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                apiKey,
                principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.setCurrentTenantId(principal.getTenantId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}

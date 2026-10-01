package com.wsnsa.payment.config;

import com.wsnsa.payment.common.Crypto;
import com.wsnsa.payment.merchant.MerchantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code X-API-Key} → MERCHANT эрх, {@code X-Admin-Key} → ADMIN эрх.
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final MerchantService merchantService;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/admin/")) {
            String adminKey = request.getHeader(ADMIN_KEY_HEADER);
            if (Crypto.constantTimeEquals(adminKey, props.adminApiKey())) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            }
        } else if (path.startsWith("/api/")) {
            merchantService.authenticate(request.getHeader(API_KEY_HEADER)).ifPresent(p ->
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            p, null, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")))));
        }
        chain.doFilter(request, response);
    }
}

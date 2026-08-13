package com.novax.leadora.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MdcUserFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";
    private static final String MDC_USER_EMAIL = "userEmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean populated = false;
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (subject != null) {
                MDC.put(MDC_USER_ID, subject);
            }
            if (email != null) {
                MDC.put(MDC_USER_EMAIL, email);
            }
            populated = true;
        } else {
            // Dev-only fallback for manual tooling via X-User-Id header
            String xUserId = request.getHeader("X-User-Id");
            if (xUserId != null && !xUserId.isBlank()) {
                MDC.put(MDC_USER_ID, xUserId.trim());
                populated = true;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (populated) {
                MDC.remove(MDC_USER_ID);
                MDC.remove(MDC_USER_EMAIL);
            }
        }
    }
}

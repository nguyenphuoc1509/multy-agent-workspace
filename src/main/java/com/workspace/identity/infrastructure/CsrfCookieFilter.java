package com.workspace.identity.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security loads the CSRF token lazily, so the XSRF-TOKEN cookie would only appear after the
 * token is first used. Touching it here makes every response (including 401s) carry the cookie, which
 * is how the SPA obtains a token before its first mutating request.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attr = request.getAttribute("_csrf");
        if (attr instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}

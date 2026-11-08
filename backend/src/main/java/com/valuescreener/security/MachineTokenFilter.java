package com.valuescreener.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates "Authorization: Bearer &lt;token&gt;" against a SHA-256 hash. Anything else (other scheme,
 * different case, extra whitespace, empty token) is passed on unauthenticated; the chain's entry point answers 401.
 */
public class MachineTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final String expectedSha256Hex;
    private final String authority;

    public MachineTokenFilter(String expectedSha256Hex, String authority) {
        this.expectedSha256Hex = expectedSha256Hex;
        this.authority = authority;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request.getHeader("Authorization"));
        if (token != null && TokenHasher.matches(token, expectedSha256Hex)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new PreAuthenticatedAuthenticationToken(
                    "machine", null, List.of(new SimpleGrantedAuthority(authority))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private static String extractToken(String header) {
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length());
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return token;
    }
}

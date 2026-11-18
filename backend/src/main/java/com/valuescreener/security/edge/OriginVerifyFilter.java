package com.valuescreener.security.edge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lets only requests through that carry the secret header CloudFront adds on the way to the origin, so the
 * HTTP API cannot be called around CloudFront. Runs before Spring Security (highest precedence), also for
 * /api/auth/*. Without a configured secret (local development) the filter does nothing. A rejected request
 * gets 403 with an empty body and no other headers; neither the secret nor the presented value is logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OriginVerifyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Verify";

    private final byte[] secret;

    @Autowired
    public OriginVerifyFilter(@Value("${app.security.origin-verify.secret:}") String secret,
                              @Value("${app.security.origin-verify.required:false}") boolean required) {
        if (required && (secret == null || secret.isBlank())) {
            // Fail closed: in AWS a missing secret parameter must stop the start, not switch the check off.
            throw new IllegalStateException("app.security.origin-verify.required is true but "
                    + "app.security.origin-verify.secret is not set (check the SSM parameter "
                    + "/value-screener/app.security.origin-verify.secret)");
        }
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    public OriginVerifyFilter(String secret) {
        this(secret, false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secret == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        // isEqual compares in constant time for equal lengths; the length itself is not a secret worth hiding here.
        if (presented != null && MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}

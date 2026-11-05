package com.valuescreener.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address used for throttling. X-Forwarded-For is only honoured behind a trusted
 * proxy (app.security.trust-forwarded-for=true); then the rightmost entry is used, because that is the
 * one the proxy appended and the client cannot forge.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null) {
                String[] parts = header.split(",");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String candidate = parts[i].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }
}

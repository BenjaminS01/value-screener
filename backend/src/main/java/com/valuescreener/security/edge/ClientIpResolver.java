package com.valuescreener.security.edge;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address used for throttling.
 * <ul>
 *   <li>Behind CloudFront (origin-verify secret configured AND app.security.client-ip-header set): that
 *       header (CloudFront-Viewer-Address, format ip:port) is used. Only then is it trustworthy, because
 *       the origin-verify filter has proven the request came through CloudFront.</li>
 *   <li>Otherwise X-Forwarded-For is only honoured behind a trusted proxy
 *       (app.security.trust-forwarded-for=true); then the rightmost entry is used, because that is the
 *       one the proxy appended and the client cannot forge.</li>
 *   <li>Fallback: the remote address of the connection.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;
    private final String viewerHeader;

    public ClientIpResolver(boolean trustForwardedFor) {
        this(trustForwardedFor, "", "");
    }

    @Autowired
    public ClientIpResolver(@Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor,
                            @Value("${app.security.origin-verify.secret:}") String originSecret,
                            @Value("${app.security.client-ip-header:}") String clientIpHeader) {
        this.trustForwardedFor = trustForwardedFor;
        boolean originVerifyActive = originSecret != null && !originSecret.isBlank();
        this.viewerHeader = originVerifyActive && clientIpHeader != null && !clientIpHeader.isBlank()
                ? clientIpHeader.trim() : null;
    }

    public String resolve(HttpServletRequest request) {
        if (viewerHeader != null) {
            String address = stripPort(request.getHeader(viewerHeader));
            if (address != null) {
                return address;
            }
        } else if (trustForwardedFor) {
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

    /** "203.0.113.7:51234" -> "203.0.113.7"; "2001:db8::1:51234" and "[2001:db8::1]:51234" -> "2001:db8::1". */
    private static String stripPort(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        int colon = v.lastIndexOf(':');
        if (colon <= 0) {
            return null;
        }
        String address = v.substring(0, colon);
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.substring(1, address.length() - 1);
        }
        return address.isBlank() ? null : address;
    }
}

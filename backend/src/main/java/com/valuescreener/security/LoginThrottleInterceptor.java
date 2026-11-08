package com.valuescreener.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Optional;

/**
 * Per-IP throttle for the login endpoint. Runs before body binding, so malformed bodies are counted as
 * attempts too. Rejects with 429 instead of delaying the thread.
 */
public class LoginThrottleInterceptor implements HandlerInterceptor {

    static final String CLIENT_IP_ATTRIBUTE = LoginThrottleInterceptor.class.getName() + ".clientIp";

    private final LoginAttemptGuard guard;
    private final ClientIpResolver ipResolver;

    public LoginThrottleInterceptor(LoginAttemptGuard guard, ClientIpResolver ipResolver) {
        this.guard = guard;
        this.ipResolver = ipResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String ip = ipResolver.resolve(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, ip);
        Optional<Duration> blocked = guard.ipBlockRemaining(ip);
        if (blocked.isEmpty()) {
            guard.recordAttempt(ip);
            // The attempt that exceeds the limit is rejected itself, not only the following ones.
            blocked = guard.ipBlockRemaining(ip);
        }
        if (blocked.isPresent()) {
            Duration remaining = blocked.get();
            long seconds = remaining.toSeconds() + (remaining.toNanosPart() > 0 ? 1 : 0);
            response.setHeader("Retry-After", Long.toString(Math.max(1, seconds)));
            JsonSecurityErrorHandlers.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
            LoginAudit.failure(null, ip, "IP_THROTTLED");
            return false;
        }
        return true;
    }
}

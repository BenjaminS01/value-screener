package com.valuescreener.security.auth;

import com.valuescreener.persistence.ConcurrentUpdateException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import com.valuescreener.security.throttle.LoginThrottleInterceptor;

/** Keeps the {"error":"..."} body for malformed requests to the auth endpoints. */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static String ip(HttpServletRequest request) {
        Object resolved = request.getAttribute(LoginThrottleInterceptor.CLIENT_IP_ATTRIBUTE);
        return resolved != null ? resolved.toString() : request.getRemoteAddr();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(HttpServletRequest request) {
        LoginAudit.failure(null, ip(request), "MALFORMED");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request"));
    }

    /**
     * The per-IP throttle state could not be updated (retries exhausted under heavy contention). That
     * update happens before the password is checked, so refusing the login here skips nothing: no attempt
     * goes uncounted and no session is issued; the client may retry. Account failures cannot end here,
     * they are counted by an atomic update.
     */
    @ExceptionHandler(ConcurrentUpdateException.class)
    public ResponseEntity<Map<String, String>> throttleStateBusy(HttpServletRequest request) {
        LoginAudit.failure(null, ip(request), "THROTTLE_STATE_CONFLICT");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
                .body(Map.of("error", "Login temporarily unavailable"));
    }
}

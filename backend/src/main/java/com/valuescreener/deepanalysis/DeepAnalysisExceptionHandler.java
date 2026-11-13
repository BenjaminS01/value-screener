package com.valuescreener.deepanalysis;

import com.valuescreener.limits.LimitReachedException;
import com.valuescreener.research.CompanySnapshotNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Scoped to this controller so other controllers' error handling is unchanged. */
@RestControllerAdvice(assignableTypes = DeepAnalysisController.class)
public class DeepAnalysisExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DeepAnalysisExceptionHandler.class);

    @ExceptionHandler(CompanySnapshotNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Snapshot not found"));
    }

    @ExceptionHandler(LimitReachedException.class)
    public ResponseEntity<Map<String, String>> limitReached(LimitReachedException e) {
        String message = e.scope() == LimitReachedException.Scope.GLOBAL
                ? "Tageslimit der Anwendung erreicht"
                : "Tageslimit für die Tiefenanalyse erreicht";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", message));
    }

    @ExceptionHandler(DeepAnalysisUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(DeepAnalysisUnavailableException e) {
        log.warn("Deep analysis unavailable", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Tiefenanalyse nicht verfügbar"));
    }
}

package com.valuescreener.research;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Group-catalog errors for the catalog controller and the research controllers that assign groups
 * (UnknownGroupException -> 422 must reach them). IllegalArgumentException is deliberately NOT handled
 * here: CompanySnapshotExceptionHandler already maps it to 400 for the research controllers, and
 * {@link StockGroupIllegalArgumentHandler} does so (with a body) for StockGroupController only.
 */
@RestControllerAdvice(assignableTypes = {StockGroupController.class, CompanySnapshotController.class,
        ResearchIngestController.class})
public class StockGroupExceptionHandler {

    @ExceptionHandler(StockGroupNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(StockGroupNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(StockGroupConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(StockGroupConflictException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(UnknownGroupException.class)
    public ResponseEntity<Map<String, String>> handleUnknownGroup(UnknownGroupException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}

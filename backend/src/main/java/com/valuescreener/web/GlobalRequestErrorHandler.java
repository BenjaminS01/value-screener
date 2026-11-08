package com.valuescreener.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Uniform {"error":"Invalid request"} 400 for malformed requests. Lowest precedence: the scoped advices
 * (e.g. AuthExceptionHandler, which also audits the failure) must win where they overlap.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalRequestErrorHandler {

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request"));
    }
}

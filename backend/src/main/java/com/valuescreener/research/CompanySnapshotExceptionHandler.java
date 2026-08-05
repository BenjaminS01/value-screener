package com.valuescreener.research;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanySnapshotController.class)
public class CompanySnapshotExceptionHandler {

    @ExceptionHandler(CompanySnapshotNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(CompanySnapshotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}

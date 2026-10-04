package com.paytm.seatreservation.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<?> domain(DomainException e, HttpServletRequest req) {
        return ResponseEntity.status(e.status()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", e.status().value(),
                "code", e.code(),
                "message", e.getMessage(),
                "request_id", req.getAttribute("request_id") == null ? "" : req.getAttribute("request_id")
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "code", "validation_error",
                "message", "Request validation failed",
                "request_id", req.getAttribute("request_id") == null ? "" : req.getAttribute("request_id")
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e, HttpServletRequest req) {
        return ResponseEntity.internalServerError().body(Map.of(
                "status", 500,
                "code", "internal_error",
                "message", "Internal server error",
                "request_id", req.getAttribute("request_id") == null ? "" : req.getAttribute("request_id")
        ));
    }
}

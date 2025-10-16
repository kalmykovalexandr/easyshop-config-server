package com.easyshop.config.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Centralizes translation of server-side exceptions into RFC 7807 Problem Detail responses.
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(HttpServletRequest request, MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setType(URI.create("https://easyshop.dev/errors/validation"));

        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toValidationField)
            .collect(Collectors.toList());
        if (!fields.isEmpty()) {
            problemDetail.setProperty("errors", fields);
        }
        addTrace(problemDetail);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint(HttpServletRequest request, ConstraintViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setType(URI.create("https://easyshop.dev/errors/validation"));

        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
            .map(this::toValidationField)
            .collect(Collectors.toList());
        if (!violations.isEmpty()) {
            problemDetail.setProperty("errors", violations);
        }
        addTrace(problemDetail);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(HttpServletRequest request, BusinessException ex) {
        ErrorCode code = ex.getCode();
        ProblemDetail problemDetail = ProblemDetail.forStatus(code.getStatus());
        problemDetail.setTitle(code.getDefaultMessage());
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setType(code.toUri());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        addTrace(problemDetail);
        return ResponseEntity.status(code.getStatus()).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(HttpServletRequest request, Exception ex) {
        log.error("Unexpected error while processing {}", request.getRequestURI(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal error");
        problemDetail.setDetail("Please contact support if the issue persists.");
        problemDetail.setType(URI.create("https://easyshop.dev/errors/internal"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        addTrace(problemDetail);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private Map<String, String> toValidationField(FieldError error) {
        Map<String, String> field = new LinkedHashMap<>();
        field.put("field", error.getField());
        field.put("message", error.getDefaultMessage());
        if (error.getCode() != null) {
            field.put("code", error.getCode());
        }
        return field;
    }

    private Map<String, String> toValidationField(ConstraintViolation<?> violation) {
        Map<String, String> field = new LinkedHashMap<>();
        field.put("field", violation.getPropertyPath().toString());
        field.put("message", violation.getMessage());
        String annotation = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        field.put("code", annotation);
        return field;
    }

    private void addTrace(ProblemDetail problemDetail) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            problemDetail.setProperty("traceId", traceId);
        }
    }
}

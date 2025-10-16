package com.easyshop.config.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Canonical application error catalogue used to drive {@link ProblemDetail} responses.
 */
public enum ErrorCode {
    EMAIL_IN_USE(HttpStatus.CONFLICT, "Email already in use"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private static final String BASE_TYPE = "https://easyshop.dev/errors/";

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public URI toUri() {
        return URI.create(BASE_TYPE + slug());
    }

    private String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

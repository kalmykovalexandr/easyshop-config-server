# Unified Error Handling for `AuthController#register`

This approach uses Spring Boot 3's built-in [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) support so that
**every** registration endpoint responds with the same JSON envelope. Front-end code only needs to parse a single structure,
while services keep their logic focused on business rules.

## Response Contract

Typical error payload returned by the controllers:

```json
{
  "type": "https://easyshop.dev/errors/email-in-use",
  "title": "Email already in use",
  "status": 409,
  "detail": "User with email alice@example.com already exists",
  "instance": "/api/auth/register",
  "errors": [
    {"field": "email", "message": "must be a well-formed email address", "code": "Email"}
  ],
  "traceId": "9f3e2b7a1..."
}
```

* `errors` appears **only** for validation problems (`@Valid`, `@Validated`).
* `traceId` lets support correlate logs (populate via MDC in a servlet filter or tracing interceptor).

## Domain Exceptions

```java
public enum ErrorCode {
    EMAIL_IN_USE(HttpStatus.CONFLICT, "Email already in use"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
}

public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
```

*Add new entries to `ErrorCode` as your domain grows.* Services throw `BusinessException` instead of returning
`Optional`/`boolean` flags. That keeps the happy path clean and lets the handler do the HTTP translation.

Example usage in the registration service:

```java
if (userRepository.existsByEmail(request.email())) {
    throw new BusinessException(
        ErrorCode.EMAIL_IN_USE,
        "User with email %s already exists".formatted(request.email())
    );
}
```

## Global Controller Advice

`GlobalErrorHandler` centralises the mapping of Java exceptions to `ProblemDetail` responses.

```java
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(HttpServletRequest request, MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setType(URI.create("https://easyshop.dev/errors/validation"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("errors", fieldErrors(ex));
        addTrace(pd);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint(HttpServletRequest request, ConstraintViolationException ex) { /* ... */ }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(HttpServletRequest request, BusinessException ex) {
        ErrorCode code = ex.getCode();
        ProblemDetail pd = ProblemDetail.forStatus(code.getStatus());
        pd.setTitle(code.getDefaultMessage());
        pd.setDetail(ex.getMessage());
        pd.setType(code.toUri());
        pd.setInstance(URI.create(request.getRequestURI()));
        addTrace(pd);
        return ResponseEntity.status(code.getStatus()).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(HttpServletRequest request, Exception ex) { /* ... */ }

    private void addTrace(ProblemDetail pd) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            pd.setProperty("traceId", traceId);
        }
    }
}
```

Key points:

* Controllers stay minimal—`@PostMapping` methods can `throw` and let the advice build the response.
* Validation errors include a machine-readable array of `field`/`message`/`code` elements.
* Business errors use canonical URIs generated from `ErrorCode`.
* All unexpected exceptions collapse into a generic `500` response with a support message and traceId.

## Putting It Together

1. Annotate controller inputs with `@Valid` / `@Validated` for structural validation.
2. Throw `BusinessException` inside services/helpers to represent domain failures.
3. Allow everything to bubble into `GlobalErrorHandler`.
4. Ensure a servlet filter or tracing library pushes a `traceId` into `MDC` for every request.

With these pieces in place, registration endpoints will always emit the same schema, HTTP status codes will reflect the
problem category, and the front end can rely on stable error contracts.

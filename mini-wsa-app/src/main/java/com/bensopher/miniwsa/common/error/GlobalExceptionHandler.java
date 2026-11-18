package com.bensopher.miniwsa.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Turns every error into an RFC 9457 problem ({@code application/problem+json}). Spring MVC's own exceptions
 * (415, 405, 404, ...) are handled by the base class in the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A hint for well-behaved clients to back off rather than retry immediately. */
    static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail handleValidation(RequestValidationException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Validation failed",
                ex.getMessage(), request);
        problem.setProperty("errors", ex.violations());
        return problem;
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, ProblemTypes.PAYLOAD_TOO_LARGE, "Payload too large",
                ex.getMessage(), request);
    }

    /** Missing or unknown API key. The detail is fixed text: the presented key is never echoed. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException ex, HttpServletRequest request) {
        String detail = ex instanceof BadCredentialsException
                ? "The API key is not valid"
                : "An API key is required in the X-API-Key header";
        return problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized", detail, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden",
                "This API key does not have the role required for this endpoint", request);
    }

    /** A read query ran past the read-query timeout and the driver cancelled it. */
    @ExceptionHandler(QueryTimeoutException.class)
    ResponseEntity<ProblemDetail> handleQueryTimeout(QueryTimeoutException ex, HttpServletRequest request) {
        log.warn("Query timed out on {} {}", request.getMethod(), request.getRequestURI());
        return serviceUnavailable(
                "The query took too long and was cancelled. Narrow the time range or try again later.", request);
    }

    /**
     * No database connection within the pool's 2s wait: the pool is exhausted or the database is unreachable.
     * Failing fast with a 503 is the backpressure: requests don't queue up without bound, and clients retry
     * later. Inside a transaction the error surfaces as CannotCreateTransactionException.
     */
    @ExceptionHandler({CannotGetJdbcConnectionException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> handleNoConnection(Exception ex, HttpServletRequest request) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.warn("No database connection for {} {}: {}", request.getMethod(), request.getRequestURI(),
                cause.getMessage());
        return serviceUnavailable("The service is temporarily overloaded. Try again shortly.", request);
    }

    /** Anything unexpected: a generic body (no exception text leaks), full details in the log under an id. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled error {} on {} {}", errorId, request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL,
                "Internal server error", "An unexpected error occurred. Reference: " + errorId, request);
        problem.setProperty("errorId", errorId);
        return problem;
    }

    /** Unreadable bodies: the parser's message may quote the input, so it is never echoed back. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // A chunked body that crosses the size limit fails while the parser is reading it, so the limit
        // error arrives wrapped in the parser's exception.
        PayloadTooLargeException tooLarge = findCause(ex, PayloadTooLargeException.class);
        if (tooLarge != null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, tooLarge.getMessage());
            problem.setType(ProblemTypes.PAYLOAD_TOO_LARGE);
            problem.setTitle("Payload too large");
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is missing or is not valid JSON");
        problem.setType(ProblemTypes.MALFORMED_JSON);
        problem.setTitle("Malformed JSON");
        return ResponseEntity.badRequest().body(problem);
    }

    /** A query parameter that can't be converted, such as {@code configId=abc}: a 400 in the validation format. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Class<?> type = ex.getRequiredType();
        String message = type == Long.class || type == Integer.class ? "must be an integer" : "has an invalid value";
        RequestValidationException validation = new RequestValidationException("Invalid query parameters",
                List.of(new FieldViolation(null, ex.getPropertyName(), message, ex.getValue())));
        return ResponseEntity.badRequest()
                .body(handleValidation(validation, ((ServletWebRequest) request).getRequest()));
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static ResponseEntity<ProblemDetail> serviceUnavailable(String detail, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.SERVICE_UNAVAILABLE, "Service unavailable",
                        detail, request));
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}

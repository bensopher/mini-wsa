package com.bensopher.miniwsa.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.context.request.ServletWebRequest;

import java.net.URI;
import java.sql.SQLTransientConnectionException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events/ingest");

    @Test
    void validationErrorsBecomeA400WithTheirViolations() {
        List<FieldViolation> violations = List.of(
                new FieldViolation(1, "rule.severity", "must be one of [CRITICAL, HIGH, MEDIUM, LOW]", "SEVERE"));

        ProblemDetail problem = handler.handleValidation(
                new RequestValidationException("1 of 2 events failed validation; nothing was stored", violations),
                request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION);
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getDetail()).isEqualTo("1 of 2 events failed validation; nothing was stored");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/v1/events/ingest"));
        assertThat(problem.getProperties()).containsEntry("errors", violations);
    }

    @Test
    void tooLargeBecomesA413() {
        ProblemDetail problem = handler.handlePayloadTooLarge(
                new PayloadTooLargeException("A batch may contain at most 1000 events"), request);

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getType()).isEqualTo(ProblemTypes.PAYLOAD_TOO_LARGE);
        assertThat(problem.getDetail()).isEqualTo("A batch may contain at most 1000 events");
    }

    @Test
    void unexpectedErrorsDoNotLeakTheirMessage() {
        ProblemDetail problem = handler.handleUnexpected(
                new IllegalStateException("password=hunter2 at db-internal-host:5432"), request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getType()).isEqualTo(ProblemTypes.INTERNAL);
        assertThat(problem.getDetail()).doesNotContain("hunter2", "db-internal-host")
                .startsWith("An unexpected error occurred. Reference: ");
        assertThat(problem.getProperties()).containsKey("errorId");
        assertThat(problem.getDetail()).endsWith((String) problem.getProperties().get("errorId"));
    }

    @Test
    void aQueryTimeoutBecomesA503WithRetryAfter() {
        ResponseEntity<ProblemDetail> response = handler.handleQueryTimeout(
                new QueryTimeoutException("canceling statement due to user request"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail())
                .isEqualTo("The query took too long and was cancelled. Narrow the time range or try again later.");
    }

    @Test
    void noDatabaseConnectionBecomesA503WithRetryAfterWithOrWithoutATransaction() {
        SQLTransientConnectionException poolTimeout = new SQLTransientConnectionException(
                "HikariPool-1 - Connection is not available, request timed out after 2001ms");

        for (Exception ex : new Exception[] {
                new CannotCreateTransactionException("Could not open JDBC Connection for transaction", poolTimeout),
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", poolTimeout)}) {
            ResponseEntity<ProblemDetail> response = handler.handleNoConnection(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
            assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getDetail())
                    .isEqualTo("The service is temporarily overloaded. Try again shortly.");
        }
    }

    @Test
    void unreadableBodiesBecomeMalformedJsonWithoutEchoingTheParserMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Unexpected character ('<' (code 60)) near <script>", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(request));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(problem.getType()).isEqualTo(ProblemTypes.MALFORMED_JSON);
        assertThat(problem.getDetail()).isEqualTo("The request body is missing or is not valid JSON");
    }
}

package com.bensopher.miniwsa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Logs every HTTP exchange an integration test makes: method, path, status, and the request and response bodies
 * (cut to {@value #MAX_BODY_CHARS} characters), so a test's output shows what the server actually returned.
 * Headers are not logged, so API keys never appear in the build output.
 */
class ExchangeLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ExchangeLoggingInterceptor.class);
    private static final int MAX_BODY_CHARS = 1000;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        // The body stream can only be read once: keep a copy and hand the test a response that replays it.
        byte[] responseBody = response.getBody().readAllBytes();
        String query = request.getURI().getRawQuery() == null ? "" : "?" + request.getURI().getRawQuery();
        log.info("{} {}{} -> {}\n  request body:  {}\n  response body: {}",
                request.getMethod(), request.getURI().getRawPath(), query, response.getStatusCode().value(),
                abbreviate(body), abbreviate(responseBody));
        return new ReplayableResponse(response, responseBody);
    }

    private static String abbreviate(byte[] bytes) {
        if (bytes.length == 0) {
            return "(empty)";
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.length() <= MAX_BODY_CHARS
                ? text
                : text.substring(0, MAX_BODY_CHARS) + "... (" + text.length() + " chars)";
    }

    private record ReplayableResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}

package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.common.error.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Caps request bodies. A declared {@code Content-Length} over the limit is rejected before anything is read;
 * a body without one (chunked) is counted while it is read and rejected as soon as it crosses the limit.
 * Runs first in the filter chain, before security, so no work is spent on an oversized request.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver exceptionResolver;

    public RequestBodyLimitFilter(long maxBytes, HandlerExceptionResolver exceptionResolver) {
        this.maxBytes = maxBytes;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            // Outside Spring MVC: hand the error to MVC's resolver so it is rendered as the usual problem.
            exceptionResolver.resolveException(request, response, null, tooLarge());
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request), response);
    }

    private PayloadTooLargeException tooLarge() {
        return new PayloadTooLargeException("The request body must not exceed " + maxBytes + " bytes");
    }

    private class LimitedBodyRequest extends HttpServletRequestWrapper {

        private ServletInputStream limited;

        LimitedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limited == null) {
                limited = new CountingInputStream(super.getInputStream());
            }
            return limited;
        }
    }

    private class CountingInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        CountingInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int bytes) {
            count += bytes;
            if (count > maxBytes) {
                throw tooLarge();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

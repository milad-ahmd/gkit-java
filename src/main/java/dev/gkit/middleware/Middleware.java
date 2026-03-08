package dev.gkit.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Composable servlet filters for production HTTP services.
 *
 * <pre>{@code
 * @Bean
 * public FilterRegistrationBean<Middleware.LoggingFilter> loggingFilter() {
 *     var reg = new FilterRegistrationBean<>(new Middleware.LoggingFilter());
 *     reg.addUrlPatterns("/*");
 *     return reg;
 * }
 * }</pre>
 */
public final class Middleware {

    private Middleware() {}

    // ---- Request ID ---------------------------------------------------------

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static class RequestIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
            String id = req.getHeader(REQUEST_ID_HEADER);
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            res.setHeader(REQUEST_ID_HEADER, id);
            req.setAttribute(REQUEST_ID_HEADER, id);
            chain.doFilter(req, res);
        }
    }

    // ---- Logging ------------------------------------------------------------

    public static class LoggingFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
            long start = System.currentTimeMillis();
            StatusCapturingWrapper wrapped = new StatusCapturingWrapper(res);
            try {
                chain.doFilter(req, wrapped);
            } finally {
                long ms = System.currentTimeMillis() - start;
                String reqId = (String) req.getAttribute(REQUEST_ID_HEADER);
                log.info("{} {} {} {}ms id={}", req.getMethod(), req.getRequestURI(),
                        wrapped.getStatus(), ms, reqId);
            }
        }
    }

    // ---- Recovery (panic/exception) ----------------------------------------

    public static class RecoveryFilter extends OncePerRequestFilter {
        private static final Logger log = LoggerFactory.getLogger(RecoveryFilter.class);

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
            try {
                chain.doFilter(req, res);
            } catch (Exception e) {
                log.error("Unhandled exception: {}", req.getRequestURI(), e);
                if (!res.isCommitted()) {
                    res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
                }
            }
        }
    }

    // ---- Timeout ------------------------------------------------------------

    public static class TimeoutFilter extends OncePerRequestFilter {
        private final Duration timeout;

        public TimeoutFilter(Duration timeout) { this.timeout = timeout; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<?> future = exec.submit(() -> {
                try { chain.doFilter(req, res); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                if (!res.isCommitted()) {
                    res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Request timeout");
                }
            } catch (Exception e) {
                throw new ServletException(e);
            } finally {
                exec.shutdownNow();
            }
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private static class StatusCapturingWrapper extends HttpServletResponseWrapper {
        private int status = 200;

        StatusCapturingWrapper(HttpServletResponse response) { super(response); }

        @Override public void setStatus(int sc) { this.status = sc; super.setStatus(sc); }
        @Override public void sendError(int sc) throws IOException { this.status = sc; super.sendError(sc); }
        @Override public void sendError(int sc, String msg) throws IOException { this.status = sc; super.sendError(sc, msg); }

        int getStatus() { return status; }
    }
}

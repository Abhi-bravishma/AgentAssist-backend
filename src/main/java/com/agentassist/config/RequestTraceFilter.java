package com.agentassist.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tags every request with a short id that appears on all of its log lines.
 *
 * <p>Without this, a slow response is untraceable: twenty agents are served
 * concurrently, each request emits a dozen lines across the AI, RAG and
 * Salesforce classes, and the log is an interleaved blur with no way to tell
 * which lines belong together.
 *
 * <p>The id is echoed back as {@code X-Request-Id}, so a slow call in the
 * browser's Network tab carries the exact string to grep the server log for.
 * An inbound {@code X-Request-Id} is honoured, which means a caller (or nginx,
 * via {@code proxy_set_header X-Request-Id $request_id}) can correlate its own
 * logs with this one.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceFilter implements Filter {

    /** MDC key rendered by the log pattern. */
    public static final String REQ_ID = "reqId";
    /** MDC key set by the processing pipeline once the interaction is known. */
    public static final String INTERACTION = "interaction";

    private static final String HEADER = "X-Request-Id";
    private static final int ID_LENGTH = 8;
    private static final int MAX_INBOUND_LENGTH = 24;
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String id = null;
        if (request instanceof HttpServletRequest http) {
            id = http.getHeader(HEADER);
        }
        if (id == null || id.isBlank()) {
            id = generateId();
        } else if (id.length() > MAX_INBOUND_LENGTH) {
            // Never let a caller-supplied header bloat every log line.
            id = id.substring(0, MAX_INBOUND_LENGTH);
        }

        MDC.put(REQ_ID, id);
        if (response instanceof HttpServletResponse http) {
            http.setHeader(HEADER, id);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses threads; a leaked MDC would mislabel the next request.
            MDC.remove(REQ_ID);
            MDC.remove(INTERACTION);
        }
    }

    private static String generateId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

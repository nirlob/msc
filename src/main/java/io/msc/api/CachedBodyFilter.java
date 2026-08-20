package io.msc.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Caches the request body bytes so they can be read multiple times. Needed
 * for signed submissions, where the same bytes must be read once for
 * signature verification and once for Jackson deserialisation.
 *
 * Also publishes a {@link ThreadLocal} with the current request, so the
 * {@link SubmissionController} can pull the cached body without having to
 * declare an extra parameter that would break the generated interface
 * signature.
 */
@Component
public class CachedBodyFilter extends OncePerRequestFilter {

    public static final String ATTR_RAW_BODY = "msc.rawBody";

    /** Thread-local current request, used to retrieve cached body bytes. */
    private static final ThreadLocal<HttpServletRequest> CURRENT =
            ThreadLocal.withInitial(() -> null);

    /** Called from controllers to get the cached body bytes for the current request. */
    public static byte[] currentBody() {
        HttpServletRequest req = CURRENT.get();
        if (req == null) return null;
        Object attr = req.getAttribute(ATTR_RAW_BODY);
        return attr instanceof byte[] bytes ? bytes : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        boolean cached = "POST".equals(request.getMethod())
                && "/msc/send".equals(request.getRequestURI());

        if (cached) {
            byte[] raw = request.getInputStream().readAllBytes();
            request.setAttribute(ATTR_RAW_BODY, raw);
            CURRENT.set(request);
            try {
                HttpServletRequest wrapped = new CachedBodyRequest(request, raw);
                filterChain.doFilter(wrapped, response);
            } finally {
                CURRENT.remove();
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static final class CachedBodyRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest delegate, byte[] body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(
                        jakarta.servlet.ReadListener l) { /* no-op */ }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(),
                                                           StandardCharsets.UTF_8));
        }
    }
}
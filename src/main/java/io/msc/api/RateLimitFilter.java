package io.msc.api;

import io.msc.service.ContributionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Per-IP / per-origin rate limit. Applies to POST /msc/contributions only.
 * Headers: RateLimit-Limit (the lower of the two buckets), RateLimit-Remaining.
 * 429 when either bucket overflows.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ContributionService service;

    public RateLimitFilter(ContributionService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        if (!"POST".equals(req.getMethod()) || !"/msc/contributions".equals(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String ip = req.getRemoteAddr();
        String origin = req.getHeader("MSC-Origin");
        if (origin == null || origin.isBlank()) origin = "unknown";
        String window = DateTimeFormatter.ofPattern("yyyyMMddHH")
                                          .withZone(ZoneOffset.UTC)
                                          .format(Instant.now());

        int ipCount   = service.incrementRate("ip:" + ip, window);
        int orgCount  = service.incrementRate("origin:" + origin, window);

        int limit = Math.min(ContributionService.RATE_LIMIT_PER_IP,
                             ContributionService.RATE_LIMIT_PER_ORIGIN);
        int remaining = Math.min(
            ContributionService.RATE_LIMIT_PER_IP - ipCount,
            ContributionService.RATE_LIMIT_PER_ORIGIN - orgCount);

        if (ipCount > ContributionService.RATE_LIMIT_PER_IP
                || orgCount > ContributionService.RATE_LIMIT_PER_ORIGIN) {
            res.setStatus(429);
            res.setHeader("RateLimit-Limit", String.valueOf(
                ipCount > ContributionService.RATE_LIMIT_PER_IP
                    ? ContributionService.RATE_LIMIT_PER_IP
                    : ContributionService.RATE_LIMIT_PER_ORIGIN));
            res.setHeader("RateLimit-Remaining", "0");
            res.setContentType("application/json");
            res.getWriter().write(
                "{\"error\":\"rate_limited\",\"message\":\"rate limit exceeded\"}");
            return;
        }

        res.setHeader("RateLimit-Limit", String.valueOf(limit));
        res.setHeader("RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        chain.doFilter(req, res);
    }
}
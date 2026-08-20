package io.msc.api.generated;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Minimal stub for the OpenAPI Generator's {@code ApiUtil}.
 *
 * The Spring template's default impl calls into a Spring MVC helper to set
 * example response bodies on the request. We don't need that functionality
 * (our controllers set their own examples via OpenAPI annotations), so we
 * provide a no-op implementation that the generated code can compile against.
 *
 * Located in {@code io.msc.api.generated} so it's visible to all generated
 * APIs without us touching generated sources.
 */
public final class ApiUtil {

    private ApiUtil() {}

    public static void setExampleResponse(NativeWebRequest request,
                                          String contentType,
                                          String example) {
        // no-op
    }

    public static void setExampleResponse(HttpServletRequest request,
                                          String contentType,
                                          String example) {
        // no-op
    }
}
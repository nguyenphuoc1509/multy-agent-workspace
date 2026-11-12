package com.workspace.shared.published;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** An error that maps to an application/problem+json response with a stable machine-readable code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extras;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    public ApiException(HttpStatus status, String code, String detail, Map<String, Object> extras) {
        super(detail);
        this.status = status;
        this.code = code;
        this.extras = new LinkedHashMap<>(extras);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, Object> extras() { return extras; }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", detail);
    }

    public static ApiException unauthenticated(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", detail);
    }

    public static ApiException validation(String detail, List<FieldIssue> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail, Map.of("fields", fields));
    }

    public static ApiException validation(String detail) {
        return validation(detail, List.of());
    }
}

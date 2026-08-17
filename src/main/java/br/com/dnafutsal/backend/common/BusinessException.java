package br.com.dnafutsal.backend.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> diagnostics;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null, Map.of());
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause,
                             Map<String, ?> diagnostics) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.diagnostics = immutableDiagnostics(diagnostics);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> diagnostics() {
        return diagnostics;
    }

    private static Map<String, Object> immutableDiagnostics(Map<String, ?> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        diagnostics.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}

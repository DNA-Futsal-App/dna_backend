package br.com.dnafutsal.backend.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class Errors {

    private Errors() {
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessException badRequest(String code, String message, Throwable cause,
                                               Map<String, ?> diagnostics) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message, cause, diagnostics);
    }

    public static BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    public static BusinessException notFound(String code, String message, Throwable cause,
                                             Map<String, ?> diagnostics) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message, cause, diagnostics);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException tooManyRequests(String code, String message) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static BusinessException dependencyUnavailable(String code, String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public static BusinessException dependencyUnavailable(String code, String message, Throwable cause,
                                                           Map<String, ?> diagnostics) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, message, cause, diagnostics);
    }

    public static BusinessException badGateway(String code, String message, Throwable cause,
                                               Map<String, ?> diagnostics) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, code, message, cause, diagnostics);
    }

    public static BusinessException badGateway(String code, String message) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, code, message);
    }

    public static BusinessException gatewayTimeout(String code, String message, Throwable cause,
                                                   Map<String, ?> diagnostics) {
        return new BusinessException(HttpStatus.GATEWAY_TIMEOUT, code, message, cause, diagnostics);
    }
}

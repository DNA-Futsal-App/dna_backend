package br.com.dnafutsal.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception, HttpServletRequest request) {
        logBusiness(exception, request);
        return response(exception.status(), exception.code(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException exception,
                                                         HttpServletRequest request) {
        String parameter = exception.getParameterName();
        Map<String, String> fields = Map.of(parameter, "é obrigatório");
        return rejected(HttpStatus.BAD_REQUEST, "REQUEST_PARAMETER_MISSING",
                "O parâmetro obrigatório '" + parameter + "' não foi informado.", request, fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                     HttpServletRequest request) {
        String parameter = exception.getName();
        Map<String, String> fields = Map.of(parameter, expectedFormat(exception.getRequiredType()));
        return rejected(HttpStatus.BAD_REQUEST, "REQUEST_PARAMETER_INVALID",
                "O parâmetro '" + parameter + "' possui formato inválido.", request, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception,
                                                         HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            String name = parameter == null ? "parameter" : parameter;
            result.getResolvableErrors().stream()
                    .map(error -> error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage())
                    .findFirst()
                    .ifPresent(message -> fields.putIfAbsent(name, message));
        });
        return rejected(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Existem parâmetros inválidos na requisição.", request, fields);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return rejected(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Existem campos inválidos na requisição.", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException exception,
                                                   HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String property = violation.getPropertyPath().toString();
            int separator = property.lastIndexOf('.');
            String field = separator < 0 ? property : property.substring(separator + 1);
            fields.putIfAbsent(field, violation.getMessage());
        });
        return rejected(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Existem parâmetros inválidos na requisição.", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception,
                                                   HttpServletRequest request) {
        return rejected(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "O corpo da requisição é inválido.", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException exception, HttpServletRequest request) {
        return rejected(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Você não tem permissão para executar esta operação.", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return rejected(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "O recurso solicitado não foi encontrado.", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        Throwable rootCause = rootCause(exception);
        log.error("Unexpected request failure requestId={} method={} path={} status=500 code=INTERNAL_ERROR "
                        + "exceptionType={} rootCauseType={}",
                RequestIdFilter.from(request), request.getMethod(), request.getRequestURI(),
                exception.getClass().getName(), rootCause.getClass().getName(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Não foi possível concluir a operação.", request, null);
    }

    private ResponseEntity<ProblemDetail> rejected(HttpStatus status, String code, String message,
                                                    HttpServletRequest request, Map<String, String> fields) {
        log.warn("Request rejected requestId={} method={} path={} status={} code={}",
                RequestIdFilter.from(request), request.getMethod(), request.getRequestURI(), status.value(), code);
        return response(status, code, message, request, fields);
    }

    private void logBusiness(BusinessException exception, HttpServletRequest request) {
        if (exception.status().is5xxServerError()) {
            log.error("Request failed requestId={} method={} path={} status={} code={} diagnostics={}",
                    RequestIdFilter.from(request), request.getMethod(), request.getRequestURI(),
                    exception.status().value(), exception.code(), exception.diagnostics(), exception);
            return;
        }
        log.warn("Request rejected requestId={} method={} path={} status={} code={} diagnostics={}",
                RequestIdFilter.from(request), request.getMethod(), request.getRequestURI(),
                exception.status().value(), exception.code(), exception.diagnostics());
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String message,
                                                   HttpServletRequest request, Map<String, String> fields) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("requestId", RequestIdFilter.from(request));
        if (fields != null && !fields.isEmpty()) {
            problem.setProperty("fields", fields);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private String expectedFormat(Class<?> requiredType) {
        if (requiredType == LocalDate.class) {
            return "deve ser uma data no formato AAAA-MM-DD";
        }
        if (requiredType != null && (Number.class.isAssignableFrom(requiredType) || requiredType.isPrimitive())) {
            return "deve ser um número válido";
        }
        return "possui formato inválido";
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

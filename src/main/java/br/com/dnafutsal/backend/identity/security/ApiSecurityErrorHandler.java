package br.com.dnafutsal.backend.identity.security;

import br.com.dnafutsal.backend.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import tools.jackson.databind.ObjectMapper;

public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityErrorHandler.class);

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "É necessário autenticar-se para acessar este recurso.", exception);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Você não tem permissão para acessar este recurso.", exception);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String code, String detail, RuntimeException exception) throws IOException {
        String requestId = RequestIdFilter.from(request);
        log.warn("Security request rejected requestId={} method={} path={} status={} code={} exceptionType={}",
                requestId, request.getMethod(), request.getRequestURI(), status.value(), code,
                exception.getClass().getName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("requestId", requestId);

        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(RequestIdFilter.HEADER_NAME, requestId);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

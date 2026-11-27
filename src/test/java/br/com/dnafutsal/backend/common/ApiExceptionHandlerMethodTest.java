package br.com.dnafutsal.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerMethodTest {
    @Test
    void unsupportedGetReturns405AndAllowPost() {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/test/reconcile");
        var exception = new HttpRequestMethodNotSupportedException("GET", List.of("POST"));
        var result = new ApiExceptionHandler().handleMethodNotSupported(exception, request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
    }
}

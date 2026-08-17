package br.com.dnafutsal.backend.news.infrastructure;

import br.com.dnafutsal.backend.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpNewsGatewayTest {

    private static final String BASE_URL = "https://news.example";

    @Test
    void requestsPublishedNewsWithoutApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNewsGateway gateway = new HttpNewsGateway(builder, properties());
        server.expect(requestTo(BASE_URL + "/api/v1/public/news?page=0&size=20&sort=publishedAt,desc"))
                .andExpect(headerDoesNotExist("X-Api-Key"))
                .andRespond(withSuccess("""
                        {
                          "content": [],
                          "page": 0,
                          "size": 20,
                          "totalElements": 0,
                          "totalPages": 0
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.published(0, 20).content()).isEmpty();
        server.verify();
    }

    private IntegrationProperties properties() {
        IntegrationProperties.SportsEndpoint sports = new IntegrationProperties.SportsEndpoint(
                "https://sports.example", Duration.ofSeconds(5), Duration.ofSeconds(60),
                Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(10), false);
        return new IntegrationProperties(sports, new IntegrationProperties.Endpoint(BASE_URL));
    }
}

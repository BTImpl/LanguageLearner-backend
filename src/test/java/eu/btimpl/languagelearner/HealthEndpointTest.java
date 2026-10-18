package eu.btimpl.languagelearner;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.class)
class HealthEndpointTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  void healthIsUpWithoutAuthentication() throws Exception {
    URI uri = URI.create("http://localhost:" + port + "/actuator/health");
    HttpRequest request = HttpRequest.newBuilder(uri).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void livenessIsUpWithoutAuthentication() throws Exception {
    URI uri = URI.create("http://localhost:" + port + "/actuator/health/liveness");
    HttpRequest request = HttpRequest.newBuilder(uri).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}

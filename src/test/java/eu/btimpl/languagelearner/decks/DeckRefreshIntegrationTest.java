package eu.btimpl.languagelearner.decks;

import static eu.btimpl.languagelearner.sheets.FakeSheetsClient.row;
import static org.assertj.core.api.Assertions.assertThat;

import eu.btimpl.languagelearner.TestClock;
import eu.btimpl.languagelearner.auth.AuthTestSupport;
import eu.btimpl.languagelearner.sheets.FakeSheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The cache and {@code POST /api/v1/decks/refresh} over real HTTP, with the default durations, a
 * clock the test moves, and a sheet in memory. The students of the tests are told apart so that
 * what one test leaves in the cache does not matter to another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthTestSupport.Config.class)
class DeckRefreshIntegrationTest {

  @TestConfiguration
  static class Sheet {

    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }

    @Bean
    @Primary
    FakeSheetsClient fakeSheet() {
      return new FakeSheetsClient()
          .tab(
              "Students",
              row("email", "name", "tab"),
              row("one@example.com", "One", "One"),
              row("two@example.com", "Two", "Two"),
              row("three@example.com", "Three", "Three"),
              row("four@example.com", "Four", "Four"))
          .tab("One", row("english", "hungarian", "deck"), row("one", "egy", "Numbers"))
          .tab("Two", row("english", "hungarian", "deck"), row("two", "kettő", "Numbers"))
          .tab("Three", row("english", "hungarian", "deck"), row("three", "három", "Numbers"))
          .tab("Four", row("english", "hungarian", "deck"), row("four", "négy", "Numbers"));
    }
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired private FakeSheetsClient sheet;
  @Autowired private TestClock clock;

  private HttpResponse<String> send(String method, String email) throws Exception {
    String path = "POST".equals(method) ? "/api/v1/decks/refresh" : "/api/v1/decks";
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Bearer " + AuthTestSupport.tokenOf(email))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void twoGetsReadTheSheetOnlyOnce() throws Exception {
    send("GET", "one@example.com");
    int afterFirst = sheet.reads();

    HttpResponse<String> second = send("GET", "one@example.com");

    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(second.body()).contains("\"english\":\"one\"");
    assertThat(sheet.reads()).isEqualTo(afterFirst);
  }

  @Test
  void refreshReadsTheSheetAgainAndAnswersLikeTheGet() throws Exception {
    send("GET", "two@example.com");
    sheet.tab("Two", row("english", "hungarian", "deck"), row("deux", "kettő", "French"));
    clock.advance(Duration.ofMinutes(1));

    HttpResponse<String> refreshed = send("POST", "two@example.com");
    HttpResponse<String> get = send("GET", "two@example.com");

    assertThat(refreshed.statusCode()).isEqualTo(200);
    assertThat(refreshed.body()).contains("\"name\":\"French\"", "\"english\":\"deux\"");
    assertThat(refreshed.body()).doesNotContain("\"english\":\"two\"");
    assertThat(get.body()).isEqualTo(refreshed.body());
  }

  @Test
  void aSecondRefreshWithinTenSecondsReturnsTheCachedDecks() throws Exception {
    HttpResponse<String> first = send("POST", "three@example.com");
    sheet.tab("Three", row("english", "hungarian", "deck"), row("trois", "három", "French"));
    clock.advance(Duration.ofSeconds(5));

    HttpResponse<String> second = send("POST", "three@example.com");

    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(second.body()).isEqualTo(first.body());
  }

  @Test
  void whenTheSheetIsDownTheEarlierDecksComeBack() throws Exception {
    HttpResponse<String> earlier = send("GET", "four@example.com");
    clock.advance(Duration.ofMinutes(1));
    sheet.failing("Four", new SheetsException.Unavailable("down", null));

    HttpResponse<String> refreshed = send("POST", "four@example.com");

    assertThat(refreshed.statusCode()).isEqualTo(200);
    assertThat(refreshed.body()).isEqualTo(earlier.body());
  }

  @Test
  void refreshNeedsASignedInStudent() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/decks/refresh"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(401);
  }
}

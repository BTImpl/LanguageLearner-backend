package eu.btimpl.languagelearner.decks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.btimpl.languagelearner.auth.AuthTestSupport;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The deck endpoint over real HTTP, with the sheet replaced by an in-memory one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthTestSupport.Config.class)
class DeckApiIntegrationTest {

  @TestConfiguration
  static class FixedClock {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-07T07:12:00.789Z"), ZoneOffset.UTC);
    }
  }

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;

  private static List<String> row(String... cells) {
    return Arrays.asList(cells);
  }

  @BeforeEach
  void theSheetHasTwoStudentsWithWords() {
    when(sheetsClient.readTab("Students"))
        .thenReturn(
            List.of(
                row("email", "name", "tab"),
                row("tomi@example.com", "Bodis Tamas", "Tomi"),
                row("anna@example.com", "Kiss Anna", "Anna"),
                row("nobody@example.com", "No Tab Exists", "Nobody"),
                row("notab@example.com", "Blank Tab", "")));
    // Like the real Sheets API, rows end after the last filled cell.
    when(sheetsClient.readTab("Tomi"))
        .thenReturn(
            List.of(
                row("english", "hungarian", "deck", "example"),
                row("ticket", "jegy", "Travel"),
                row("boarding pass", "beszállókártya", "Travel"),
                row("luggage; baggage", "poggyász", "Travel", "My luggage is heavy."),
                row("apple", "alma", "Food")));
    when(sheetsClient.readTab("Anna"))
        .thenReturn(List.of(row("english", "hungarian", "deck"), row("dog", "kutya", "Animals")));
    when(sheetsClient.readTab("Nobody")).thenThrow(new SheetsException.TabNotFound("Nobody", null));
  }

  private HttpResponse<String> get(String path, String bearerToken) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (bearerToken != null) {
      request.header("Authorization", "Bearer " + bearerToken);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void returnsTheStudentsDecksWithTheirWords() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/decks", AuthTestSupport.tokenOf("tomi@example.com"));

    String body = response.body();
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body).startsWith("[{\"id\":\"");
    assertThat(body).contains("\"name\":\"Food\"", "\"name\":\"Travel\"");
    assertThat(body.indexOf("\"name\":\"Food\"")).isLessThan(body.indexOf("\"name\":\"Travel\""));
    assertThat(body).contains("\"english\":\"boarding pass\"", "\"hungarian\":\"beszállókártya\"");
    assertThat(body).contains("\"acceptedEnglish\":[\"luggage\",\"baggage\"]");
    assertThat(body).contains("\"acceptedHungarian\":[\"jegy\"]");
    assertThat(body).contains("\"example\":\"My luggage is heavy.\"", "\"example\":null");
  }

  @Test
  void theWordsKeepTheOrderOfTheSheet() throws Exception {
    String body = get("/api/v1/decks", AuthTestSupport.tokenOf("tomi@example.com")).body();

    int ticket = body.indexOf("\"english\":\"ticket\"");
    int boardingPass = body.indexOf("\"english\":\"boarding pass\"");
    int luggage = body.indexOf("\"english\":\"luggage\"");
    assertThat(ticket).isNotNegative().isLessThan(boardingPass);
    assertThat(boardingPass).isLessThan(luggage);
  }

  @Test
  void everyDeckCarriesWhenTheSheetWasRead() throws Exception {
    String body = get("/api/v1/decks", AuthTestSupport.tokenOf("tomi@example.com")).body();

    assertThat(body.split("\"updatedAt\":\"2026-10-07T07:12:00Z\"", -1)).hasSize(3);
  }

  @Test
  void theStudentSeesTheirOwnTabOnlyWhateverTheRequestSays() throws Exception {
    String annasToken = AuthTestSupport.tokenOf("anna@example.com");

    HttpResponse<String> response =
        get("/api/v1/decks?tab=Tomi&email=tomi@example.com", annasToken);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"name\":\"Animals\"", "\"english\":\"dog\"");
    assertThat(response.body()).doesNotContain("ticket");
    verify(sheetsClient, never()).readTab("Tomi");
  }

  @Test
  void aStudentWhoseTabDoesNotExistGetsAnEmptyList() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/decks", AuthTestSupport.tokenOf("nobody@example.com"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");
  }

  @Test
  void aStudentWithoutATabGetsAnEmptyList() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/decks", AuthTestSupport.tokenOf("notab@example.com"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");
    verify(sheetsClient, never()).readTab("");
  }

  @Test
  void anUnreadableSheetIsServiceUnavailable() throws Exception {
    when(sheetsClient.readTab("Tomi"))
        .thenThrow(new SheetsException.Unavailable("Sheets is down", null));

    HttpResponse<String> response =
        get("/api/v1/decks", AuthTestSupport.tokenOf("tomi@example.com"));

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"code\":\"SHEET_UNAVAILABLE\"");
  }

  @Test
  void withoutATokenItIsUnauthorized() throws Exception {
    HttpResponse<String> response = get("/api/v1/decks", null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
  }

  @Test
  void aStudentNotOnTheListIsForbidden() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/decks", AuthTestSupport.tokenOf("stranger@example.com"));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"code\":\"STUDENT_NOT_FOUND\"");
  }

  @Test
  void theOpenApiDescriptionListsTheEndpointsAndTheTokenScheme() throws Exception {
    HttpResponse<String> response =
        get("/v3/api-docs", AuthTestSupport.tokenOf("tomi@example.com"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"/api/v1/decks\"", "\"/api/v1/me\"", "\"bearerAuth\"", "\"acceptedEnglish\"");
  }

  @Test
  void theOpenApiDescriptionIsNotForStrangers() throws Exception {
    assertThat(get("/v3/api-docs", null).statusCode()).isEqualTo(401);
    assertThat(get("/v3/api-docs", AuthTestSupport.tokenOf("stranger@example.com")).statusCode())
        .isEqualTo(403);
  }
}

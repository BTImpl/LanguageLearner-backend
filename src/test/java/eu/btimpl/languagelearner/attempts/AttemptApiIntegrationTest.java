package eu.btimpl.languagelearner.attempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import eu.btimpl.languagelearner.TestDatabase;
import eu.btimpl.languagelearner.auth.AuthTestSupport;
import eu.btimpl.languagelearner.decks.Deck;
import eu.btimpl.languagelearner.sheets.DeckReader;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Uploading answers over real HTTP, into a real PostgreSQL, with the sheet in memory. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthTestSupport.Config.class, TestDatabase.class})
@TestPropertySource(properties = {"app.cache.students-ttl=0s", "app.cache.decks-ttl=0s"})
class AttemptApiIntegrationTest {

  private static final String TOMI = "tomi@example.com";
  private static final String ANNA = "anna@example.com";
  private static final String ANSWERED_AT = "2026-10-07T12:03:11Z";

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;
  @MockitoSpyBean private StudentRegistry registry;
  @Autowired private DeckReader deckReader;
  @Autowired private StudentRepository students;
  @Autowired private AttemptRepository attempts;

  private static List<String> row(String... cells) {
    return Arrays.asList(cells);
  }

  @BeforeEach
  void theSheetHasTwoStudentsWithWords() {
    when(sheetsClient.readTab("Students"))
        .thenReturn(
            List.of(
                row("email", "name", "tab"),
                row(TOMI, "Bodis Tamas", "Tomi"),
                row(ANNA, "Kiss Anna", "Anna")));
    when(sheetsClient.readTab("Tomi"))
        .thenReturn(
            List.of(
                row("english", "hungarian", "deck"),
                row("ticket", "jegy", "Travel"),
                row("boarding pass", "beszállókártya", "Travel"),
                row("apple", "alma", "Food")));
    when(sheetsClient.readTab("Anna"))
        .thenReturn(List.of(row("english", "hungarian", "deck"), row("dog", "kutya", "Animals")));
  }

  // --- helpers ---------------------------------------------------------------------------

  private Deck deckOf(String tab, String name) {
    return deckReader.readDecks(tab).stream()
        .filter(deck -> deck.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private String travelWordId(String english) {
    return deckOf("Tomi", "Travel").words().stream()
        .filter(word -> word.english().equals(english))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private String item(UUID id, String deckId, String wordId, String mode, String outcome) {
    return """
        {"id":"%s","deckId":"%s","wordId":"%s","mode":"%s","outcome":"%s","answeredAt":"%s"}"""
        .formatted(id, deckId, wordId, mode, outcome, ANSWERED_AT);
  }

  private String tomiItem(UUID id, String english, String mode, String outcome) {
    return item(id, deckOf("Tomi", "Travel").id(), travelWordId(english), mode, outcome);
  }

  private static String batch(String... items) {
    return "{\"attempts\":[" + String.join(",", items) + "]}";
  }

  private HttpResponse<String> post(String body, String email) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/attempts"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (email != null) {
      request.header("Authorization", "Bearer " + AuthTestSupport.tokenOf(email));
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private List<Attempt> stored(String email) {
    return students
        .findByEmail(email)
        .map(student -> attempts.findByStudent(student.id()))
        .orElse(List.of());
  }

  private void assertBadRequest(String body) throws Exception {
    HttpResponse<String> response = post(body, TOMI);

    assertThat(response.statusCode()).as(body).isEqualTo(400);
    assertThat(response.body()).contains("\"code\":\"BAD_REQUEST\"");
  }

  // --- saving ----------------------------------------------------------------------------

  @Test
  void savesTheAnswersWithTheWordCopiedFromTheDecks() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String body =
        batch(
            tomiItem(first, "ticket", "QUIZ", "FIRST_TRY"),
            tomiItem(second, "boarding pass", "TYPING", "WITH_HELP"));

    HttpResponse<String> response = post(body, TOMI);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"accepted\":2,\"duplicates\":0}");
    List<Attempt> saved = stored(TOMI);
    assertThat(saved).extracting(Attempt::id).contains(first, second);
    Attempt ticket = saved.stream().filter(a -> a.id().equals(first)).findFirst().orElseThrow();
    assertThat(ticket.deckName()).isEqualTo("Travel");
    assertThat(ticket.english()).isEqualTo("ticket");
    assertThat(ticket.hungarian()).isEqualTo("jegy");
    assertThat(ticket.mode()).isEqualTo(Mode.QUIZ);
    assertThat(ticket.outcome()).isEqualTo(Outcome.FIRST_TRY);
    assertThat(ticket.answeredAt()).isEqualTo(Instant.parse(ANSWERED_AT));
    assertThat(ticket.receivedAt()).isAfter(Instant.parse(ANSWERED_AT));
    Attempt boardingPass =
        saved.stream().filter(a -> a.id().equals(second)).findFirst().orElseThrow();
    assertThat(boardingPass.hungarian()).isEqualTo("beszállókártya");
    assertThat(boardingPass.mode()).isEqualTo(Mode.TYPING);
    assertThat(boardingPass.outcome()).isEqualTo(Outcome.WITH_HELP);
  }

  @Test
  void theSameBatchSentTwiceOnlyReportsDuplicatesTheSecondTime() throws Exception {
    String body =
        batch(
            tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "FIRST_TRY"),
            tomiItem(UUID.randomUUID(), "boarding pass", "MIX", "WRONG_FIRST"));

    HttpResponse<String> first = post(body, TOMI);
    int afterFirst = stored(TOMI).size();
    HttpResponse<String> second = post(body, TOMI);

    assertThat(first.body()).isEqualTo("{\"accepted\":2,\"duplicates\":0}");
    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(second.body()).isEqualTo("{\"accepted\":0,\"duplicates\":2}");
    assertThat(stored(TOMI)).hasSize(afterFirst);
  }

  @Test
  void aBatchWithOldAndNewAnswersSavesOnlyTheNewOnes() throws Exception {
    UUID known = UUID.randomUUID();
    post(batch(tomiItem(known, "ticket", "QUIZ", "FIRST_TRY")), TOMI);

    HttpResponse<String> response =
        post(
            batch(
                tomiItem(known, "ticket", "QUIZ", "FIRST_TRY"),
                tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "WRONG_FIRST")),
            TOMI);

    assertThat(response.body()).isEqualTo("{\"accepted\":1,\"duplicates\":1}");
  }

  @Test
  void anIdRepeatedInOneBatchIsSavedOnce() throws Exception {
    UUID id = UUID.randomUUID();

    HttpResponse<String> response =
        post(
            batch(
                tomiItem(id, "ticket", "QUIZ", "FIRST_TRY"),
                tomiItem(id, "ticket", "QUIZ", "FIRST_TRY")),
            TOMI);

    assertThat(response.body()).isEqualTo("{\"accepted\":1,\"duplicates\":1}");
  }

  @Test
  void aWordThatIsNotInTheDecksIsSavedWithEmptyCopies() throws Exception {
    UUID id = UUID.randomUUID();
    String deckId = deckOf("Tomi", "Travel").id();

    HttpResponse<String> response =
        post(batch(item(id, deckId, "gone-word", "QUIZ", "FIRST_TRY")), TOMI);

    assertThat(response.body()).isEqualTo("{\"accepted\":1,\"duplicates\":0}");
    Attempt saved = stored(TOMI).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    assertThat(saved.deckName()).isEqualTo("Travel");
    assertThat(saved.english()).isEmpty();
    assertThat(saved.hungarian()).isEmpty();
    assertThat(saved.wordId()).isEqualTo("gone-word");
  }

  @Test
  void aDeckThatIsNotThereIsSavedWithEmptyCopies() throws Exception {
    UUID id = UUID.randomUUID();

    post(batch(item(id, "gone-deck", "gone-word", "FLASHCARDS", "FIRST_TRY")), TOMI);

    Attempt saved = stored(TOMI).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    assertThat(saved.deckName()).isEmpty();
    assertThat(saved.english()).isEmpty();
    assertThat(saved.deckId()).isEqualTo("gone-deck");
  }

  @Test
  void theAnswersAreSavedForTheStudentOfTheTokenOnly() throws Exception {
    UUID id = UUID.randomUUID();
    Deck annasDeck = deckOf("Anna", "Animals");
    String annasWordId = annasDeck.words().getFirst().id();

    post(batch(item(id, annasDeck.id(), annasWordId, "QUIZ", "FIRST_TRY")), ANNA);

    assertThat(stored(ANNA)).extracting(Attempt::id).contains(id);
    assertThat(stored(TOMI)).extracting(Attempt::id).doesNotContain(id);
    assertThat(stored(ANNA).getFirst().english()).isEqualTo("dog");
  }

  @Test
  void aStudentCannotBorrowTheWordsOfAnotherStudent() throws Exception {
    UUID id = UUID.randomUUID();
    Deck annasDeck = deckOf("Anna", "Animals");
    String annasWordId = annasDeck.words().getFirst().id();

    post(batch(item(id, annasDeck.id(), annasWordId, "QUIZ", "FIRST_TRY")), TOMI);

    Attempt saved = stored(TOMI).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    assertThat(saved.deckName()).isEmpty();
    assertThat(saved.english()).isEmpty();
  }

  @Test
  void anEmptyBatchIsFine() throws Exception {
    HttpResponse<String> response = post(batch(), TOMI);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"accepted\":0,\"duplicates\":0}");
  }

  @Test
  void fiveHundredAnswersAreFine() throws Exception {
    List<String> items = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      items.add(tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "FIRST_TRY"));
    }

    HttpResponse<String> response = post(batch(items.toArray(String[]::new)), TOMI);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"accepted\":500,\"duplicates\":0}");
  }

  // --- invalid requests ------------------------------------------------------------------

  @Test
  void moreThanFiveHundredAnswersAreRejected() throws Exception {
    List<String> items = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      items.add(tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "FIRST_TRY"));
    }

    assertBadRequest(batch(items.toArray(String[]::new)));
  }

  @Test
  void anUnknownModeOrOutcomeIsRejected() throws Exception {
    assertBadRequest(batch(tomiItem(UUID.randomUUID(), "ticket", "DANCING", "FIRST_TRY")));
    assertBadRequest(batch(tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "MAYBE")));
    assertBadRequest(batch(tomiItem(UUID.randomUUID(), "ticket", "quiz", "FIRST_TRY")));
  }

  @Test
  void aMissingFieldIsRejected() throws Exception {
    UUID id = UUID.randomUUID();
    assertBadRequest(
        "{\"attempts\":[{\"deckId\":\"d\",\"wordId\":\"w\",\"mode\":\"QUIZ\","
            + "\"outcome\":\"FIRST_TRY\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + id
            + "\",\"wordId\":\"w\",\"mode\":\"QUIZ\","
            + "\"outcome\":\"FIRST_TRY\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + id
            + "\",\"deckId\":\"d\",\"mode\":\"QUIZ\","
            + "\"outcome\":\"FIRST_TRY\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + id
            + "\",\"deckId\":\"d\",\"wordId\":\"w\","
            + "\"outcome\":\"FIRST_TRY\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + id
            + "\",\"deckId\":\"d\",\"wordId\":\"w\","
            + "\"mode\":\"QUIZ\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + id
            + "\",\"deckId\":\"d\",\"wordId\":\"w\","
            + "\"mode\":\"QUIZ\",\"outcome\":\"FIRST_TRY\"}]}");
    assertBadRequest("{}");
    assertBadRequest("{\"attempts\":null}");
    assertBadRequest("{\"attempts\":[null]}");
  }

  @Test
  void blankIdsAreRejected() throws Exception {
    assertBadRequest(batch(item(UUID.randomUUID(), "", "w", "QUIZ", "FIRST_TRY")));
    assertBadRequest(batch(item(UUID.randomUUID(), "d", "  ", "QUIZ", "FIRST_TRY")));
  }

  @Test
  void aBadUuidOrTimeIsRejected() throws Exception {
    assertBadRequest(
        "{\"attempts\":[{\"id\":\"not-a-uuid\",\"deckId\":\"d\",\"wordId\":\"w\","
            + "\"mode\":\"QUIZ\",\"outcome\":\"FIRST_TRY\",\"answeredAt\":\""
            + ANSWERED_AT
            + "\"}]}");
    assertBadRequest(
        "{\"attempts\":[{\"id\":\""
            + UUID.randomUUID()
            + "\",\"deckId\":\"d\",\"wordId\":\"w\","
            + "\"mode\":\"QUIZ\",\"outcome\":\"FIRST_TRY\",\"answeredAt\":\"yesterday\"}]}");
  }

  @Test
  void aBrokenOrMissingBodyIsRejected() throws Exception {
    assertBadRequest("{\"attempts\":[");
    assertBadRequest("");
    assertBadRequest("not json");
  }

  @Test
  void anInvalidBatchSavesNothing() throws Exception {
    UUID good = UUID.randomUUID();

    post(
        batch(
            tomiItem(good, "ticket", "QUIZ", "FIRST_TRY"),
            tomiItem(UUID.randomUUID(), "ticket", "DANCING", "FIRST_TRY")),
        TOMI);

    assertThat(stored(TOMI)).extracting(Attempt::id).doesNotContain(good);
  }

  // --- who may upload, and what if something is down ------------------------------------

  @Test
  void withoutATokenItIsUnauthorized() throws Exception {
    HttpResponse<String> response =
        post(batch(tomiItem(UUID.randomUUID(), "ticket", "QUIZ", "FIRST_TRY")), null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
  }

  @Test
  void aStudentNotOnTheListIsForbiddenAndNothingIsSaved() throws Exception {
    UUID id = UUID.randomUUID();

    HttpResponse<String> response =
        post(batch(item(id, "d", "w", "QUIZ", "FIRST_TRY")), "stranger@example.com");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"code\":\"STUDENT_NOT_FOUND\"");
    assertThat(stored("stranger@example.com")).isEmpty();
  }

  @Test
  void whenTheSheetCannotBeReadTheBatchIsRefusedSoTheAppSendsItAgain() throws Exception {
    UUID id = UUID.randomUUID();
    String body = batch(item(id, "d", "w", "QUIZ", "FIRST_TRY"));
    when(sheetsClient.readTab("Tomi"))
        .thenThrow(new SheetsException.Unavailable("Sheets is down", null));

    HttpResponse<String> response = post(body, TOMI);

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"code\":\"SHEET_UNAVAILABLE\"");
    assertThat(stored(TOMI)).extracting(Attempt::id).doesNotContain(id);
  }

  @Test
  void whenTheDatabaseIsDownTheBatchIsRefused() throws Exception {
    doThrow(new DataAccessResourceFailureException("database is down"))
        .when(registry)
        .register(any());

    HttpResponse<String> response =
        post(batch(item(UUID.randomUUID(), "d", "w", "QUIZ", "FIRST_TRY")), TOMI);

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"code\":\"DATABASE_UNAVAILABLE\"");
  }

  @Test
  void theOpenApiDescriptionListsTheEndpoint() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
            .header("Authorization", "Bearer " + AuthTestSupport.tokenOf(TOMI))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"/api/v1/attempts\"", "\"AttemptsRequest\"", "\"AttemptsResponse\"");
  }
}

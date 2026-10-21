package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.btimpl.languagelearner.TestDatabase;
import eu.btimpl.languagelearner.auth.AuthTestSupport;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The daily limit of the photo quiz endpoint over real HTTP, set to two per day. The counts live as
 * long as the application, so every test works with a student of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthTestSupport.Config.class, TestDatabase.class})
@TestPropertySource(
    properties = {
      "app.cache.students-ttl=0s",
      "app.cache.decks-ttl=0s",
      "app.quiz.limit.per-day=2"
    })
class QuizDailyLimitIntegrationTest {

  private static final String PATH = "/api/v1/quizzes/from-image";
  private static final String BOUNDARY = "----quiz-limit-boundary";
  private static final List<String> STUDENTS =
      List.of(
          "s1@example.com",
          "s2@example.com",
          "s3@example.com",
          "s4@example.com",
          "s5@example.com",
          "s6@example.com");

  private static final ExtractedQuiz QUIZ =
      new ExtractedQuiz(
          "T",
          List.of(
              new ExtractedQuiz.Question(
                  1,
                  "She ___ there.",
                  List.of(
                      new ExtractedQuiz.Option("a", "go"), new ExtractedQuiz.Option("b", "went")))),
          0);

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;
  @MockitoBean private QuizExtractor extractor;

  @BeforeEach
  void theSheetListsTheStudents() {
    List<List<String>> rows = new ArrayList<>();
    rows.add(Arrays.asList("email", "name", "tab"));
    STUDENTS.forEach(email -> rows.add(Arrays.asList(email, email, "Tab")));
    when(sheetsClient.readTab("Students")).thenReturn(rows);
  }

  private static byte[] jpeg() {
    byte[] bytes = new byte[100];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    return bytes;
  }

  private static byte[] multipart(String field, byte[] content) throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\""
                + field
                + "\"; filename=\"sheet\"\r\nContent-Type: image/jpeg\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(content);
    body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return body.toByteArray();
  }

  private HttpResponse<String> upload(String student, String field, byte[] image) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
            .header("Authorization", "Bearer " + AuthTestSupport.tokenOf(student))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(field, image)))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> upload(String student) throws Exception {
    return upload(student, "image", jpeg());
  }

  @Test
  void theRequestAfterTheLimitIsA429WithTheTimeOfTheReset() throws Exception {
    when(extractor.extract(any(), any())).thenReturn(QUIZ);
    String student = STUDENTS.get(0);
    assertThat(upload(student).statusCode()).isEqualTo(200);
    assertThat(upload(student).statusCode()).isEqualTo(200);

    Instant before = Instant.now();
    HttpResponse<String> response = upload(student);

    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(response.body())
        .contains("\"code\":\"DAILY_LIMIT_REACHED\"", "\"limit\":2", "\"resetsAt\":\"");
    Matcher resetsAt = Pattern.compile("\"resetsAt\":\"([^\"]+)\"").matcher(response.body());
    assertThat(resetsAt.find()).isTrue();
    Instant reset = Instant.parse(resetsAt.group(1));
    assertThat(reset).isAfter(before).isBeforeOrEqualTo(before.plus(Duration.ofDays(1)));
    assertThat(reset.toString()).endsWith("T00:00:00Z");
    long retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElseThrow());
    assertThat(retryAfter).isBetween(1L, 86_400L);
    // The refused request did not reach the model.
    verify(extractor, org.mockito.Mockito.times(2)).extract(any(), any());
  }

  @Test
  void everyStudentHasTheirOwnAllowance() throws Exception {
    when(extractor.extract(any(), any())).thenReturn(QUIZ);
    upload(STUDENTS.get(1));
    upload(STUDENTS.get(1));
    assertThat(upload(STUDENTS.get(1)).statusCode()).isEqualTo(429);

    assertThat(upload(STUDENTS.get(2)).statusCode()).isEqualTo(200);
  }

  @Test
  void invalidRequestsDoNotCount() throws Exception {
    when(extractor.extract(any(), any())).thenReturn(QUIZ);
    String student = STUDENTS.get(3);
    byte[] gif = "GIF89a.....".getBytes(StandardCharsets.US_ASCII);

    assertThat(upload(student, "file", jpeg()).statusCode()).isEqualTo(400);
    assertThat(upload(student, "image", new byte[0]).statusCode()).isEqualTo(400);
    assertThat(upload(student, "image", gif).statusCode()).isEqualTo(415);
    assertThat(upload(student, "image", gif).statusCode()).isEqualTo(415);

    assertThat(upload(student).statusCode()).isEqualTo(200);
    assertThat(upload(student).statusCode()).isEqualTo(200);
    assertThat(upload(student).statusCode()).isEqualTo(429);
  }

  @Test
  void aCallTheModelCouldNotAnswerDoesNotCount() throws Exception {
    String student = STUDENTS.get(4);
    when(extractor.extract(any(), any()))
        .thenThrow(new ExtractionUnavailableException("Gemini answered with HTTP 503"))
        .thenThrow(new ExtractionUnavailableException("Gemini answered with HTTP 503"))
        .thenThrow(new ExtractionUnavailableException("Gemini answered with HTTP 503"))
        .thenReturn(QUIZ);

    assertThat(upload(student).statusCode()).isEqualTo(503);
    assertThat(upload(student).statusCode()).isEqualTo(503);
    assertThat(upload(student).statusCode()).isEqualTo(503);

    assertThat(upload(student).statusCode()).isEqualTo(200);
    assertThat(upload(student).statusCode()).isEqualTo(200);
    assertThat(upload(student).statusCode()).isEqualTo(429);
  }

  @Test
  void aCallTheModelAnsweredWithoutQuestionsDoesCount() throws Exception {
    String student = STUDENTS.get(5);
    when(extractor.extract(any(), any())).thenThrow(new NoQuestionsFoundException());

    assertThat(upload(student).statusCode()).isEqualTo(422);
    assertThat(upload(student).statusCode()).isEqualTo(422);

    assertThat(upload(student).statusCode()).isEqualTo(429);
  }

  @Test
  void aRefusedStudentDoesNotCallTheModel() throws Exception {
    // A student who is not on the list never gets as far as the limit.
    HttpResponse<String> response = upload("stranger@example.com");

    assertThat(response.statusCode()).isEqualTo(403);
    verifyNoInteractions(extractor);
  }
}

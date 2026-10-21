package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The photo quiz endpoint over real HTTP, with the model replaced by a fake extractor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthTestSupport.Config.class, TestDatabase.class})
// Every test sets up its own sheet, so nothing may be kept between requests.
@TestPropertySource(
    properties = {
      "app.cache.students-ttl=0s",
      "app.cache.decks-ttl=0s",
      // The daily limit has its own test; here it must not get in the way.
      "app.quiz.limit.per-day=1000"
    })
class QuizApiIntegrationTest {

  private static final String PATH = "/api/v1/quizzes/from-image";
  private static final String BOUNDARY = "----quiz-test-boundary";
  private static final int MAX_BYTES = 8 * 1024 * 1024;

  private static final ExtractedQuiz QUIZ =
      new ExtractedQuiz(
          "Unit 5 - Travel",
          List.of(
              new ExtractedQuiz.Question(
                  1,
                  "She ___ to the airport yesterday.",
                  List.of(
                      new ExtractedQuiz.Option("a", "go"), new ExtractedQuiz.Option("b", "went")))),
          2);

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;
  @MockitoBean private QuizExtractor extractor;

  @BeforeEach
  void theSheetListsTheStudents() {
    when(sheetsClient.readTab("Students"))
        .thenReturn(
            List.of(
                Arrays.asList("email", "name", "tab"),
                Arrays.asList("tomi@example.com", "Bodis Tamas", "Tomi")));
  }

  private static byte[] jpeg(int size) {
    byte[] bytes = new byte[size];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    return bytes;
  }

  private static byte[] png(int size) {
    byte[] bytes = new byte[size];
    byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    System.arraycopy(signature, 0, bytes, 0, signature.length);
    return bytes;
  }

  private static byte[] multipart(String field, String declaredType, byte[] content)
      throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\""
                + field
                + "\"; filename=\"sheet\"\r\nContent-Type: "
                + declaredType
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(content);
    body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return body.toByteArray();
  }

  private HttpResponse<String> post(String bearerToken, String contentType, byte[] body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (bearerToken != null) {
      request.header("Authorization", "Bearer " + bearerToken);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> upload(byte[] image) throws Exception {
    return upload("image", "image/jpeg", image);
  }

  private HttpResponse<String> upload(String field, String declaredType, byte[] image)
      throws Exception {
    return post(
        AuthTestSupport.tokenOf("tomi@example.com"),
        "multipart/form-data; boundary=" + BOUNDARY,
        multipart(field, declaredType, image));
  }

  private static void assertError(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/json");
    assertThat(response.body()).contains("\"code\":\"" + code + "\"");
  }

  @Test
  void returnsTheQuizOfAJpeg() throws Exception {
    byte[] image = jpeg(2000);
    when(extractor.extract(any(), any())).thenReturn(QUIZ);

    HttpResponse<String> response = upload(image);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains(
            "\"title\":\"Unit 5 - Travel\"",
            "\"number\":1",
            "\"text\":\"She ___ to the airport yesterday.\"",
            "\"options\":[{\"label\":\"a\",\"text\":\"go\"},{\"label\":\"b\",\"text\":\"went\"}]",
            "\"skippedQuestions\":2");
    verify(extractor).extract(eq(image), eq("image/jpeg"));
  }

  @Test
  void returnsTheQuizOfAPng() throws Exception {
    byte[] image = png(2000);
    when(extractor.extract(any(), any())).thenReturn(QUIZ);

    HttpResponse<String> response = upload("image", "image/png", image);

    assertThat(response.statusCode()).isEqualTo(200);
    verify(extractor).extract(eq(image), eq("image/png"));
  }

  @Test
  void aSheetWithoutATitleHasANullTitle() throws Exception {
    when(extractor.extract(any(), any())).thenReturn(new ExtractedQuiz(null, QUIZ.questions(), 0));

    HttpResponse<String> response = upload(jpeg(100));

    assertThat(response.body()).contains("\"title\":null", "\"skippedQuestions\":0");
  }

  @Test
  void theTypeComesFromTheFileNotFromWhatTheClientDeclared() throws Exception {
    byte[] image = jpeg(500);
    when(extractor.extract(any(), any())).thenReturn(QUIZ);

    HttpResponse<String> response = upload("image", "text/plain", image);

    assertThat(response.statusCode()).isEqualTo(200);
    verify(extractor).extract(eq(image), eq("image/jpeg"));
  }

  @Test
  void anImageOfExactlyTheLimitIsAccepted() throws Exception {
    when(extractor.extract(any(), any())).thenReturn(QUIZ);

    assertThat(upload(jpeg(MAX_BYTES)).statusCode()).isEqualTo(200);
  }

  @Test
  void aMissingImageFieldIsA400() throws Exception {
    assertError(upload("file", "image/jpeg", jpeg(100)), 400, "IMAGE_MISSING");
    verifyNoInteractions(extractor);
  }

  @Test
  void anEmptyImageIsA400() throws Exception {
    assertError(upload(new byte[0]), 400, "IMAGE_MISSING");
    verifyNoInteractions(extractor);
  }

  @Test
  void aRequestThatIsNotMultipartIsA400() throws Exception {
    HttpResponse<String> response =
        post(AuthTestSupport.tokenOf("tomi@example.com"), "application/json", "{}".getBytes());

    assertError(response, 400, "IMAGE_MISSING");
    verifyNoInteractions(extractor);
  }

  @Test
  void anImageOverTheLimitIsA413() throws Exception {
    assertError(upload(jpeg(MAX_BYTES + 1)), 413, "IMAGE_TOO_LARGE");
    verifyNoInteractions(extractor);
  }

  @Test
  void aGifIsA415() throws Exception {
    byte[] gif = "GIF89a.....".getBytes(StandardCharsets.US_ASCII);

    assertError(upload("image", "image/gif", gif), 415, "IMAGE_TYPE_UNSUPPORTED");
    verifyNoInteractions(extractor);
  }

  @Test
  void aFileThatIsNotAnImageIsA415EvenWhenItClaimsToBeOne() throws Exception {
    byte[] pdf = "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII);

    assertError(upload("image", "image/jpeg", pdf), 415, "IMAGE_TYPE_UNSUPPORTED");
    verifyNoInteractions(extractor);
  }

  @Test
  void aSheetWithoutQuestionsIsA422() throws Exception {
    when(extractor.extract(any(), any())).thenThrow(new NoQuestionsFoundException());

    assertError(upload(jpeg(100)), 422, "NO_QUESTIONS_FOUND");
  }

  @Test
  void anUnavailableModelIsA503() throws Exception {
    when(extractor.extract(any(), any()))
        .thenThrow(new ExtractionUnavailableException("Gemini answered with HTTP 503"));

    HttpResponse<String> response = upload(jpeg(100));

    assertError(response, 503, "EXTRACTION_UNAVAILABLE");
    // The reason stays in the log, the app only learns that it can try again later.
    assertThat(response.body()).doesNotContain("Gemini");
  }

  @Test
  void withoutATokenItIsA401AndNothingIsRead() throws Exception {
    HttpResponse<String> response =
        post(
            null,
            "multipart/form-data; boundary=" + BOUNDARY,
            multipart("image", "image/jpeg", jpeg(100)));

    assertError(response, 401, "UNAUTHORIZED");
    verifyNoInteractions(extractor);
  }

  @Test
  void anAccountNotOnTheStudentListIsA403() throws Exception {
    HttpResponse<String> response =
        post(
            AuthTestSupport.tokenOf("stranger@example.com"),
            "multipart/form-data; boundary=" + BOUNDARY,
            multipart("image", "image/jpeg", jpeg(100)));

    assertError(response, 403, "STUDENT_NOT_FOUND");
    verifyNoInteractions(extractor);
  }

  @Test
  void theOpenApiDescriptionHasTheEndpointAndItsErrorCodes() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
            .header("Authorization", "Bearer " + AuthTestSupport.tokenOf("tomi@example.com"))
            .build();

    String api =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

    assertThat(api).contains("/api/v1/quizzes/from-image", "multipart/form-data");
    assertThat(api)
        .contains(
            "IMAGE_MISSING",
            "IMAGE_TOO_LARGE",
            "IMAGE_TYPE_UNSUPPORTED",
            "NO_QUESTIONS_FOUND",
            "EXTRACTION_UNAVAILABLE",
            "skippedQuestions");
  }
}

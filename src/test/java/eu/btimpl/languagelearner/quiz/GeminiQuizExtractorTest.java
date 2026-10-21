package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The Gemini client against a fake server: nothing here calls the real API. */
class GeminiQuizExtractorTest {

  private static final String BASE_URL = "https://gemini.test/v1beta";
  private static final String KEY = "test-key-123";
  private static final String URL = BASE_URL + "/models/gemini-3.5-flash-lite:generateContent";
  private static final byte[] PHOTO = "not really a jpeg".getBytes(StandardCharsets.UTF_8);

  private static final String QUIZ =
      """
      {"title":"Unit 5 - Travel","questions":[
        {"number":1,"text":"She ___ to the airport yesterday.",
         "options":[{"label":"a","text":"go"},{"label":"b","text":"went"}]},
        {"number":2,"text":"I ___ a ticket.",
         "options":[{"label":"a","text":"have"},{"label":"b","text":"has"},{"label":"c","text":"had"}]}
      ]}
      """;

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private MockRestServiceServer server;

  private GeminiQuizExtractor extractor() {
    return extractor(KEY);
  }

  private GeminiQuizExtractor extractor(String key) {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    return new GeminiQuizExtractor(
        builder.build(), new GeminiProperties(key, null, BASE_URL, null), mapper);
  }

  /** What Gemini sends back when the model's answer is {@code modelText}. */
  private String answer(String modelText, String finishReason) {
    return """
        {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"%s"}],
         "usageMetadata":{"promptTokenCount":1316},"modelVersion":"gemini-3.5-flash-lite"}
        """
        .formatted(mapper.writeValueAsString(modelText), finishReason);
  }

  private void respondWith(String body) {
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  @Test
  void sendsThePhotoWithThePromptAndTheSchema() {
    GeminiQuizExtractor extractor = extractor();
    String photo = Base64.getEncoder().encodeToString(PHOTO);
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", KEY))
        .andExpect(jsonPath("$.contents[0].parts[0].inline_data.mime_type").value("image/jpeg"))
        .andExpect(jsonPath("$.contents[0].parts[0].inline_data.data").value(photo))
        .andExpect(jsonPath("$.contents[0].parts[1].text").value(containsString("Do NOT answer")))
        .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
        .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
        .andExpect(jsonPath("$.generationConfig.temperature").value(0))
        .andRespond(withSuccess(answer(QUIZ, "STOP"), MediaType.APPLICATION_JSON));

    extractor.extract(PHOTO, "image/jpeg");

    server.verify();
  }

  @Test
  void keepsTheKeyOutOfTheUrl() {
    GeminiQuizExtractor extractor = extractor();
    server
        .expect(requestTo(URL))
        .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain(KEY))
        .andRespond(withSuccess(answer(QUIZ, "STOP"), MediaType.APPLICATION_JSON));

    extractor.extract(PHOTO, "image/png");

    server.verify();
  }

  @Test
  void mapsTheAnswerToAQuiz() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer(QUIZ, "STOP"));

    ExtractedQuiz quiz = extractor.extract(PHOTO, "image/jpeg");

    assertThat(quiz.title()).isEqualTo("Unit 5 - Travel");
    assertThat(quiz.droppedQuestions()).isZero();
    assertThat(quiz.questions()).hasSize(2);
    ExtractedQuiz.Question first = quiz.questions().get(0);
    assertThat(first.number()).isEqualTo(1);
    assertThat(first.text()).isEqualTo("She ___ to the airport yesterday.");
    assertThat(first.options())
        .containsExactly(
            new ExtractedQuiz.Option("a", "go"), new ExtractedQuiz.Option("b", "went"));
    assertThat(quiz.questions().get(1).options()).hasSize(3);
  }

  @Test
  void aNullTitleStaysNull() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(
        answer(
            """
            {"title":null,"questions":[{"number":1,"text":"A ___",
              "options":[{"label":"a","text":"x"},{"label":"b","text":"y"}]}]}
            """,
            "STOP"));

    assertThat(extractor.extract(PHOTO, "image/jpeg").title()).isNull();
  }

  @Test
  void dropsInvalidQuestionsAndCountsThem() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(
        answer(
            """
            {"title":"T","questions":[
              {"number":1,"text":"Fine ___","options":[{"label":"a","text":"x"},{"label":"b","text":"y"}]},
              {"number":2,"text":"","options":[{"label":"a","text":"x"},{"label":"b","text":"y"}]},
              {"number":3,"text":"One option","options":[{"label":"a","text":"x"}]}
            ]}
            """,
            "STOP"));

    ExtractedQuiz quiz = extractor.extract(PHOTO, "image/jpeg");

    assertThat(quiz.questions()).extracting(ExtractedQuiz.Question::number).containsExactly(1);
    assertThat(quiz.droppedQuestions()).isEqualTo(2);
  }

  @Test
  void ignoresFieldsItDoesNotKnow() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(
        answer(
            """
            {"title":"T","extra":1,"questions":[{"number":1,"text":"A ___","hint":"b",
              "options":[{"label":"a","text":"x","correct":true},{"label":"b","text":"y"}]}]}
            """,
            "STOP"));

    assertThat(extractor.extract(PHOTO, "image/jpeg").questions()).hasSize(1);
  }

  @Test
  void noQuestionsAtAllIsNoQuestionsFound() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer("{\"title\":null,\"questions\":[]}", "STOP"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(NoQuestionsFoundException.class);
  }

  @Test
  void onlyInvalidQuestionsIsNoQuestionsFound() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(
        answer(
            "{\"title\":null,\"questions\":[{\"number\":1,\"text\":\"Q\",\"options\":[]}]}",
            "STOP"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(NoQuestionsFoundException.class);
  }

  @Test
  void tooManyRequestsIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("too many requests or the quota");
  }

  @Test
  void aServerErrorIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("HTTP 503");
  }

  @Test
  void aRejectedKeyIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("HTTP 403");
  }

  @Test
  void anUnreachableServiceIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    server
        .expect(requestTo(URL))
        .andRespond(
            request -> {
              throw new IOException("connection reset");
            });

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("could not be reached");
  }

  @Test
  void aRequestBlockedForSafetyIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("blocked")
        .hasMessageContaining("SAFETY");
  }

  @Test
  void anAnswerStoppedForSafetyIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer(QUIZ, "SAFETY"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("SAFETY");
  }

  @Test
  void anAnswerCutOffIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer("{\"title\":null,\"questions\":[{\"number\":1,", "MAX_TOKENS"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("MAX_TOKENS");
  }

  @Test
  void noCandidateIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith("{\"candidates\":[]}");

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class);
  }

  @Test
  void anEmptyAnswerIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer("  ", "STOP"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class);
  }

  @Test
  void anAnswerThatIsNotJsonIsUnavailable() {
    GeminiQuizExtractor extractor = extractor();
    respondWith(answer("Sorry, I cannot read this image.", "STOP"));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("not a quiz");
  }

  @Test
  void aMissingKeyIsUnavailableAndNothingIsSent() {
    GeminiQuizExtractor extractor = extractor("");

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("not configured");
    server.verify();
  }

  @Test
  void theKeyIsNeverPartOfAnError() {
    GeminiQuizExtractor extractor = extractor();
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> extractor.extract(PHOTO, "image/jpeg"))
        .isInstanceOf(ExtractionUnavailableException.class)
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY));
  }

  @Test
  void rejectsAnEmptyImageOrMediaType() {
    GeminiQuizExtractor extractor = extractor();

    assertThatThrownBy(() -> extractor.extract(new byte[0], "image/jpeg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> extractor.extract(null, "image/jpeg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> extractor.extract(PHOTO, " "))
        .isInstanceOf(IllegalArgumentException.class);
    server.verify();
  }
}

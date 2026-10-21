package eu.btimpl.languagelearner.quiz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a worksheet with the Gemini API. The photo goes in as inline data and the answer is bound
 * to a JSON schema, so it is a structure and not free text. Whatever the model returns is checked
 * by {@link QuizValidator}.
 */
@Component
public class GeminiQuizExtractor implements QuizExtractor {

  private static final Logger log = LoggerFactory.getLogger(GeminiQuizExtractor.class);

  private static final String API_KEY_HEADER = "x-goog-api-key";

  /** The answer of a sheet is a few thousand tokens at most; this only stops a runaway answer. */
  private static final int MAX_OUTPUT_TOKENS = 8192;

  private final RestClient client;
  private final GeminiProperties properties;
  private final ObjectMapper mapper;
  private final String prompt;

  @Autowired
  public GeminiQuizExtractor(GeminiProperties properties, ObjectMapper mapper) {
    this(
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory(properties))
            .build(),
        properties,
        mapper);
  }

  /** For tests, which bring their own client. */
  GeminiQuizExtractor(RestClient client, GeminiProperties properties, ObjectMapper mapper) {
    this.client = client;
    this.properties = properties;
    this.mapper = mapper;
    this.prompt = ExtractionPrompt.text();
  }

  private static JdkClientHttpRequestFactory requestFactory(GeminiProperties properties) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(properties.timeout());
    return factory;
  }

  @Override
  public ExtractedQuiz extract(byte[] image, String mediaType) {
    if (image == null || image.length == 0) {
      throw new IllegalArgumentException("The image is empty");
    }
    if (mediaType == null || mediaType.isBlank()) {
      throw new IllegalArgumentException("The media type of the image is missing");
    }
    if (properties.apiKey().isEmpty()) {
      throw new ExtractionUnavailableException("The Gemini API key is not configured");
    }

    GenerateContentResponse response = call(image, mediaType);
    ModelQuiz quiz = parse(answerText(response));
    ExtractedQuiz result = QuizValidator.validate(quiz);
    if (result.droppedQuestions() > 0) {
      log.info(
          "{} incomplete question(s) were dropped, {} kept",
          result.droppedQuestions(),
          result.questions().size());
    }
    if (result.questions().isEmpty()) {
      throw new NoQuestionsFoundException();
    }
    return result;
  }

  private GenerateContentResponse call(byte[] image, String mediaType) {
    Map<String, Object> body =
        Map.of(
            "contents",
            List.of(
                Map.of(
                    "parts",
                    List.of(
                        Map.of(
                            "inline_data",
                            Map.of(
                                "mime_type",
                                mediaType,
                                "data",
                                Base64.getEncoder().encodeToString(image))),
                        Map.of("text", prompt)))),
            "generationConfig",
            Map.of(
                "temperature",
                0,
                "maxOutputTokens",
                MAX_OUTPUT_TOKENS,
                "responseMimeType",
                "application/json",
                "responseSchema",
                SCHEMA));
    try {
      GenerateContentResponse response =
          client
              .post()
              .uri("/models/{model}:generateContent", properties.model())
              .header(API_KEY_HEADER, properties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(GenerateContentResponse.class);
      if (response == null) {
        throw new ExtractionUnavailableException("Gemini sent an empty response");
      }
      return response;
    } catch (RestClientResponseException e) {
      // Only the status is reported: the body is not needed and the request is never echoed.
      int status = e.getStatusCode().value();
      if (status == 429) {
        throw new ExtractionUnavailableException(
            "Gemini refused the request: too many requests or the quota is used up", e);
      }
      throw new ExtractionUnavailableException("Gemini answered with HTTP " + status, e);
    } catch (RestClientException e) {
      throw new ExtractionUnavailableException("Gemini could not be reached", e);
    }
  }

  /** The text of the first candidate, or the reason why there is none. */
  private static String answerText(GenerateContentResponse response) {
    if (response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
      throw new ExtractionUnavailableException(
          "Gemini blocked the request: " + response.promptFeedback().blockReason());
    }
    if (response.candidates() == null || response.candidates().isEmpty()) {
      throw new ExtractionUnavailableException("Gemini returned no answer");
    }
    Candidate candidate = response.candidates().get(0);
    String finish = candidate.finishReason();
    if (finish != null && !finish.equals("STOP")) {
      // SAFETY, RECITATION, PROHIBITED_CONTENT, ... and MAX_TOKENS, whose answer is cut off.
      throw new ExtractionUnavailableException("Gemini did not finish the answer: " + finish);
    }
    StringBuilder text = new StringBuilder();
    if (candidate.content() != null && candidate.content().parts() != null) {
      for (Part part : candidate.content().parts()) {
        if (part.text() != null) {
          text.append(part.text());
        }
      }
    }
    if (text.toString().isBlank()) {
      throw new ExtractionUnavailableException("Gemini returned an empty answer");
    }
    return text.toString();
  }

  private ModelQuiz parse(String text) {
    try {
      ModelQuiz quiz = mapper.readValue(text, ModelQuiz.class);
      if (quiz == null) {
        throw new ExtractionUnavailableException("Gemini returned no quiz");
      }
      return quiz;
    } catch (JacksonException e) {
      throw new ExtractionUnavailableException("Gemini returned an answer that is not a quiz", e);
    }
  }

  /** The shape the answer is bound to (the Gemini API spells the type names in capitals). */
  private static final Map<String, Object> SCHEMA =
      Map.of(
          "type",
          "OBJECT",
          "properties",
          Map.of(
              "title",
              Map.of("type", "STRING", "nullable", true),
              "questions",
              Map.of(
                  "type",
                  "ARRAY",
                  "items",
                  Map.of(
                      "type",
                      "OBJECT",
                      "properties",
                      Map.of(
                          "number",
                          Map.of("type", "INTEGER"),
                          "text",
                          Map.of("type", "STRING"),
                          "options",
                          Map.of(
                              "type",
                              "ARRAY",
                              "items",
                              Map.of(
                                  "type",
                                  "OBJECT",
                                  "properties",
                                  Map.of(
                                      "label",
                                      Map.of("type", "STRING"),
                                      "text",
                                      Map.of("type", "STRING")),
                                  "required",
                                  List.of("label", "text")))),
                      "required",
                      List.of("number", "text", "options")))),
          "required",
          List.of("title", "questions"));

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GenerateContentResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Candidate(Content content, String finishReason) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Content(List<Part> parts) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Part(String text) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PromptFeedback(String blockReason) {}
}

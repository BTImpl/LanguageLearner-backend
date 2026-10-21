package eu.btimpl.languagelearner.quiz;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Gemini API that reads the worksheets.
 *
 * @param apiKey the API key; empty until configured, and only ever read from the environment
 * @param model the pinned model name; not an alias such as {@code -latest}, so the behaviour does
 *     not change without a deploy
 * @param baseUrl the base url of the API
 * @param timeout how long a request may take, from connecting to the last byte of the answer
 */
@ConfigurationProperties("app.quiz.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl, Duration timeout) {

  public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
  public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  public GeminiProperties {
    apiKey = apiKey == null ? "" : apiKey.strip();
    model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
    if (timeout == null) {
      timeout = DEFAULT_TIMEOUT;
    } else if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("app.quiz.gemini.timeout must be positive: " + timeout);
    }
  }

  /** The default settings, without an API key. */
  public static GeminiProperties defaults() {
    return new GeminiProperties(null, null, null, null);
  }

  @Override
  public String toString() {
    // The key must never end up in a log.
    return "GeminiProperties[model="
        + model
        + ", baseUrl="
        + baseUrl
        + ", timeout="
        + timeout
        + "]";
  }
}

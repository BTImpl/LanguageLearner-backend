package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

  @Test
  void hasPinnedDefaultsAndNoKey() {
    GeminiProperties properties = GeminiProperties.defaults();

    assertThat(properties.apiKey()).isEmpty();
    assertThat(properties.model()).isEqualTo("gemini-3.5-flash-lite").doesNotContain("latest");
    assertThat(properties.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta");
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void stripsTheValuesAndFallsBackWhenBlank() {
    GeminiProperties properties = new GeminiProperties("  key  ", " ", "", Duration.ofSeconds(5));

    assertThat(properties.apiKey()).isEqualTo("key");
    assertThat(properties.model()).isEqualTo(GeminiProperties.DEFAULT_MODEL);
    assertThat(properties.baseUrl()).isEqualTo(GeminiProperties.DEFAULT_BASE_URL);
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void rejectsATimeoutThatIsNotPositive() {
    assertThatThrownBy(() -> new GeminiProperties(null, null, null, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("app.quiz.gemini.timeout");
  }

  @Test
  void neverShowsTheKeyWhenPrinted() {
    GeminiProperties properties = new GeminiProperties("super-secret", null, null, null);

    assertThat(properties.toString()).doesNotContain("super-secret");
  }
}

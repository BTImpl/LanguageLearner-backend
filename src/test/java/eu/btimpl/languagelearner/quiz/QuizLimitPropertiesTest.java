package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuizLimitPropertiesTest {

  @Test
  void defaultsToTwentyPerDay() {
    assertThat(QuizLimitProperties.defaults().perDay()).isEqualTo(20);
    assertThat(new QuizLimitProperties(5).perDay()).isEqualTo(5);
  }

  @Test
  void rejectsALimitBelowOne() {
    assertThatThrownBy(() -> new QuizLimitProperties(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("app.quiz.limit.per-day");
    assertThatThrownBy(() -> new QuizLimitProperties(-3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

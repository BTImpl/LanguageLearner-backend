package eu.btimpl.languagelearner.quiz;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many quizzes a student may have made from photos per day. Every one is a call of a paid
 * model, so a bug or misuse must not be able to run up the bill.
 *
 * @param perDay the number of generations per student and UTC day
 */
@ConfigurationProperties("app.quiz.limit")
public record QuizLimitProperties(Integer perDay) {

  public static final int DEFAULT_PER_DAY = 20;

  public QuizLimitProperties {
    if (perDay == null) {
      perDay = DEFAULT_PER_DAY;
    } else if (perDay < 1) {
      throw new IllegalArgumentException("app.quiz.limit.per-day must be at least 1: " + perDay);
    }
  }

  /** The default limit. */
  public static QuizLimitProperties defaults() {
    return new QuizLimitProperties(null);
  }
}

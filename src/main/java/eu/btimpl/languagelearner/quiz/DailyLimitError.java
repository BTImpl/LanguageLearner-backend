package eu.btimpl.languagelearner.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The body of the 429 answer: the usual {@code code} and {@code message} of an error, and when the
 * count of the day starts again.
 */
@Schema(name = "DailyLimitError")
public record DailyLimitError(
    @Schema(description = "Always DAILY_LIMIT_REACHED", example = "DAILY_LIMIT_REACHED")
        String code,
    @Schema(description = "A short explanation for people") String message,
    @Schema(description = "How many quizzes a student may have made per day", example = "20")
        int limit,
    @Schema(
            description = "When the count starts again (the next midnight, UTC)",
            example = "2026-10-22T00:00:00Z")
        Instant resetsAt) {

  static DailyLimitError of(DailyLimitReachedException e) {
    return new DailyLimitError(
        "DAILY_LIMIT_REACHED",
        "The daily limit of " + e.limit() + " quizzes from photos is reached",
        e.limit(),
        e.resetsAt());
  }
}

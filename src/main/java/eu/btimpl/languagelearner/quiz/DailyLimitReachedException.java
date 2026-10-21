package eu.btimpl.languagelearner.quiz;

import java.time.Duration;
import java.time.Instant;

/** A student has used all the quiz generations of the day. */
final class DailyLimitReachedException extends RuntimeException {

  private final int limit;
  private final Instant resetsAt;
  private final Duration retryAfter;

  DailyLimitReachedException(int limit, Instant resetsAt, Duration retryAfter) {
    super("The daily limit of " + limit + " quizzes is reached; it resets at " + resetsAt);
    this.limit = limit;
    this.resetsAt = resetsAt;
    this.retryAfter = retryAfter;
  }

  /** How many generations a student may have per day. */
  int limit() {
    return limit;
  }

  /** When the count starts again: the next midnight, UTC. */
  Instant resetsAt() {
    return resetsAt;
  }

  /** How long from the moment of the refusal until the count starts again. */
  Duration retryAfter() {
    return retryAfter;
  }
}

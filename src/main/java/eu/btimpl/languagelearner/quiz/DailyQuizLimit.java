package eu.btimpl.languagelearner.quiz;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Counts, per student and UTC day, the quizzes made from photos, and refuses one more when the
 * limit is used up.
 *
 * <p>The counts live in the memory of the instance. They start again from zero when the instance
 * restarts, and with several instances each has its own count. With two students and Cloud Run
 * scaled to zero that is acceptable: the limit is a safety net against runaway cost, not an
 * accounting of usage, and no database table is needed for it.
 *
 * <p>A generation is reserved before the model is called, so requests that arrive together cannot
 * all slip through, and is given back when the model could not be used. That way only calls the
 * model answered are counted.
 */
@Component
class DailyQuizLimit {

  /** A generation taken from the allowance of a day; give it back with {@link #release}. */
  record Reservation(String student, LocalDate day) {}

  private record Usage(LocalDate day, int count) {}

  private final Clock clock;
  private final int limit;
  private final Map<String, Usage> usage = new HashMap<>();

  DailyQuizLimit(Clock clock, QuizLimitProperties properties) {
    this.clock = clock;
    this.limit = properties.perDay();
  }

  /**
   * Takes one generation from today's allowance of the student.
   *
   * @throws DailyLimitReachedException when the allowance is used up
   */
  synchronized Reservation reserve(String studentEmail) {
    LocalDate today = today();
    // Only today's counts matter; this also keeps the map from growing.
    usage.values().removeIf(entry -> !entry.day().equals(today));
    String student = studentEmail.strip().toLowerCase(Locale.ROOT);
    Usage current = usage.get(student);
    int used = current == null ? 0 : current.count();
    if (used >= limit) {
      Instant resetsAt = nextReset(today);
      throw new DailyLimitReachedException(
          limit, resetsAt, Duration.between(clock.instant(), resetsAt));
    }
    usage.put(student, new Usage(today, used + 1));
    return new Reservation(student, today);
  }

  /** Gives a reserved generation back, for a call the model did not answer. */
  synchronized void release(Reservation reservation) {
    Usage current = usage.get(reservation.student());
    if (current == null || !current.day().equals(reservation.day())) {
      // The day has changed since: the reservation belonged to a count that is gone.
      return;
    }
    if (current.count() <= 1) {
      usage.remove(reservation.student());
    } else {
      usage.put(reservation.student(), new Usage(current.day(), current.count() - 1));
    }
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static Instant nextReset(LocalDate today) {
    return today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}

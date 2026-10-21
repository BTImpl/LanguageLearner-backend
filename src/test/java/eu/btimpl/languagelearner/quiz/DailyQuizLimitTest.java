package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.btimpl.languagelearner.TestClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DailyQuizLimitTest {

  private static final String TOMI = "tomi@example.com";
  private static final Instant NOON = Instant.parse("2026-10-07T12:00:00Z");

  private final TestClock clock = new TestClock(NOON);

  private DailyQuizLimit limit(int perDay) {
    return new DailyQuizLimit(clock, new QuizLimitProperties(perDay));
  }

  private static void useUp(DailyQuizLimit limit, String student, int times) {
    for (int i = 0; i < times; i++) {
      limit.reserve(student);
    }
  }

  @Test
  void allowsExactlyTheLimitPerDay() {
    DailyQuizLimit limit = limit(3);

    useUp(limit, TOMI, 3);

    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void theDefaultIsTwentyPerDay() {
    DailyQuizLimit limit = new DailyQuizLimit(clock, QuizLimitProperties.defaults());

    useUp(limit, TOMI, 20);

    assertThatThrownBy(() -> limit.reserve(TOMI))
        .isInstanceOfSatisfying(
            DailyLimitReachedException.class, e -> assertThat(e.limit()).isEqualTo(20));
  }

  @Test
  void tellsWhenTheCountStartsAgain() {
    DailyQuizLimit limit = limit(1);
    limit.reserve(TOMI);

    assertThatThrownBy(() -> limit.reserve(TOMI))
        .isInstanceOfSatisfying(
            DailyLimitReachedException.class,
            e -> {
              assertThat(e.resetsAt()).isEqualTo(Instant.parse("2026-10-08T00:00:00Z"));
              assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(12));
            });
  }

  @Test
  void theCountStartsAgainAtMidnightUtc() {
    DailyQuizLimit limit = limit(2);
    useUp(limit, TOMI, 2);

    clock.advance(Duration.ofHours(12).minusSeconds(1)); // 23:59:59
    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);

    clock.advance(Duration.ofSeconds(1)); // 00:00:00 of the next day
    assertThatCode(() -> useUp(limit, TOMI, 2)).doesNotThrowAnyException();
    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void aStudentsDayDoesNotDependOnWhenTheyStarted() {
    DailyQuizLimit limit = limit(1);
    clock.advance(Duration.ofHours(11).plusMinutes(59)); // 23:59
    limit.reserve(TOMI);

    clock.advance(Duration.ofMinutes(2)); // 00:01 of the next day

    assertThatCode(() -> limit.reserve(TOMI)).doesNotThrowAnyException();
  }

  @Test
  void everyStudentHasTheirOwnCount() {
    DailyQuizLimit limit = limit(1);
    limit.reserve(TOMI);

    assertThatCode(() -> limit.reserve("anna@example.com")).doesNotThrowAnyException();
    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void theEmailAddressIsComparedWithoutCaseAndSpaces() {
    DailyQuizLimit limit = limit(1);
    limit.reserve("Tomi@Example.com");

    assertThatThrownBy(() -> limit.reserve(" tomi@example.com "))
        .isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void aReleasedReservationIsGivenBack() {
    DailyQuizLimit limit = limit(1);
    DailyQuizLimit.Reservation reservation = limit.reserve(TOMI);

    limit.release(reservation);

    assertThatCode(() -> limit.reserve(TOMI)).doesNotThrowAnyException();
  }

  @Test
  void releasingOnlyGivesBackThatOneReservation() {
    DailyQuizLimit limit = limit(2);
    DailyQuizLimit.Reservation first = limit.reserve(TOMI);
    limit.reserve(TOMI);

    limit.release(first);

    assertThatCode(() -> limit.reserve(TOMI)).doesNotThrowAnyException();
    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void aReservationOfYesterdayDoesNotGiveBackTodaysGeneration() {
    DailyQuizLimit limit = limit(1);
    DailyQuizLimit.Reservation yesterdays = limit.reserve(TOMI);
    clock.advance(Duration.ofDays(1));
    limit.reserve(TOMI);

    limit.release(yesterdays);

    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }

  @Test
  void releasingTwiceOrWithoutAnyUseChangesNothing() {
    DailyQuizLimit limit = limit(1);
    DailyQuizLimit.Reservation reservation = limit.reserve(TOMI);
    limit.release(reservation);
    limit.release(reservation);
    limit.reserve(TOMI);

    assertThatThrownBy(() -> limit.reserve(TOMI)).isInstanceOf(DailyLimitReachedException.class);
  }
}

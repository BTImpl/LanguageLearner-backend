package eu.btimpl.languagelearner.attempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.btimpl.languagelearner.TestDatabase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The attempts table on a real PostgreSQL, built by the migrations. */
@SpringBootTest
@Import(TestDatabase.class)
class AttemptRepositoryTest {

  private static final Instant ANSWERED = Instant.parse("2026-10-07T07:12:00.123Z");
  private static final Instant RECEIVED = Instant.parse("2026-10-07T07:15:30.456Z");

  @Autowired private StudentRepository students;
  @Autowired private AttemptRepository attempts;
  @Autowired private JdbcClient jdbc;

  private long newStudent() {
    return students.register(UUID.randomUUID() + "@example.com", "Student");
  }

  private static Attempt attempt(UUID id, long studentId, Instant answeredAt) {
    return new Attempt(
        id,
        studentId,
        "deck-1",
        "Travel",
        "word-1",
        "boarding pass",
        "beszállókártya",
        Mode.QUIZ,
        Outcome.FIRST_TRY,
        answeredAt,
        RECEIVED);
  }

  @Test
  void savesAnAttemptAndReadsItBackAsItWas() {
    long studentId = newStudent();
    Attempt attempt = attempt(UUID.randomUUID(), studentId, ANSWERED);

    boolean isNew = attempts.save(attempt);

    assertThat(isNew).isTrue();
    assertThat(attempts.findByStudent(studentId)).containsExactly(attempt);
  }

  @Test
  void savingTheSameIdTwiceKeepsOneAttempt() {
    long studentId = newStudent();
    UUID id = UUID.randomUUID();
    Attempt first = attempt(id, studentId, ANSWERED);
    Attempt again =
        new Attempt(
            id,
            studentId,
            "deck-1",
            "Travel",
            "word-1",
            "boarding pass",
            "beszállókártya",
            Mode.TYPING,
            Outcome.WRONG_FIRST,
            ANSWERED,
            RECEIVED);

    assertThat(attempts.save(first)).isTrue();
    assertThat(attempts.save(again)).isFalse();

    assertThat(attempts.findByStudent(studentId)).containsExactly(first);
  }

  @Test
  void theAttemptsComeOldestAnswerFirstAndOnlyThoseOfTheStudent() {
    long studentId = newStudent();
    long other = newStudent();
    Attempt later = attempt(UUID.randomUUID(), studentId, ANSWERED.plusSeconds(60));
    Attempt earlier = attempt(UUID.randomUUID(), studentId, ANSWERED);
    attempts.save(later);
    attempts.save(earlier);
    attempts.save(attempt(UUID.randomUUID(), other, ANSWERED));

    assertThat(attempts.findByStudent(studentId)).containsExactly(earlier, later);
  }

  @Test
  void everyModeAndOutcomeCanBeStored() {
    long studentId = newStudent();
    for (Mode mode : Mode.values()) {
      for (Outcome outcome : Outcome.values()) {
        Attempt attempt =
            new Attempt(
                UUID.randomUUID(),
                studentId,
                "d",
                "D",
                "w",
                "e",
                "h",
                mode,
                outcome,
                ANSWERED,
                RECEIVED);
        assertThat(attempts.save(attempt)).isTrue();
      }
    }

    assertThat(attempts.findByStudent(studentId))
        .hasSize(Mode.values().length * Outcome.values().length);
  }

  @Test
  void anAttemptNeedsAStoredStudent() {
    Attempt orphan = attempt(UUID.randomUUID(), Long.MAX_VALUE, ANSWERED);

    assertThatThrownBy(() -> attempts.save(orphan))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theDatabaseRefusesAnUnknownModeOrOutcome() {
    long studentId = newStudent();

    assertThatThrownBy(() -> insertRaw(studentId, "DANCING", "FIRST_TRY"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRaw(studentId, "QUIZ", "MAYBE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theServerTimeDefaultsToNow() {
    long studentId = newStudent();
    UUID id = UUID.randomUUID();

    jdbc.sql(
            """
            INSERT INTO attempt (id, student_id, deck_id, deck_name, word_id, english, hungarian,
                                 mode, outcome, answered_at)
            VALUES (:id, :studentId, 'd', 'D', 'w', 'e', 'h', 'QUIZ', 'FIRST_TRY', now())
            """)
        .param("id", id)
        .param("studentId", studentId)
        .update();

    Attempt stored = attempts.findByStudent(studentId).getFirst();
    assertThat(stored.receivedAt()).isAfter(Instant.now().minusSeconds(300));
  }

  private void insertRaw(long studentId, String mode, String outcome) {
    jdbc.sql(
            """
            INSERT INTO attempt (id, student_id, deck_id, deck_name, word_id, english, hungarian,
                                 mode, outcome, answered_at)
            VALUES (:id, :studentId, 'd', 'D', 'w', 'e', 'h', :mode, :outcome, now())
            """)
        .param("id", UUID.randomUUID())
        .param("studentId", studentId)
        .param("mode", mode)
        .param("outcome", outcome)
        .update();
  }
}

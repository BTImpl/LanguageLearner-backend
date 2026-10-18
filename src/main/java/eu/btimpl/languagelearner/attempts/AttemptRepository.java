package eu.btimpl.languagelearner.attempts;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The attempts table. */
@Repository
public class AttemptRepository {

  private final JdbcClient jdbc;

  public AttemptRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Stores an attempt. An attempt whose id is stored already is left as it is, which makes
   * uploading the same attempt again harmless.
   *
   * @return true when the attempt was new
   */
  public boolean save(Attempt attempt) {
    int inserted =
        jdbc.sql(
                """
                INSERT INTO attempt (id, student_id, deck_id, deck_name, word_id, english,
                                     hungarian, mode, outcome, answered_at, received_at)
                VALUES (:id, :studentId, :deckId, :deckName, :wordId, :english,
                        :hungarian, :mode, :outcome, :answeredAt, :receivedAt)
                ON CONFLICT (id) DO NOTHING
                """)
            .param("id", attempt.id())
            .param("studentId", attempt.studentId())
            .param("deckId", attempt.deckId())
            .param("deckName", attempt.deckName())
            .param("wordId", attempt.wordId())
            .param("english", attempt.english())
            .param("hungarian", attempt.hungarian())
            .param("mode", attempt.mode().name())
            .param("outcome", attempt.outcome().name())
            .param("answeredAt", StudentRepository.toDb(attempt.answeredAt()))
            .param("receivedAt", StudentRepository.toDb(attempt.receivedAt()))
            .update();
    return inserted == 1;
  }

  /** The attempts of a student, oldest answer first. */
  public List<Attempt> findByStudent(long studentId) {
    return jdbc.sql(
            """
            SELECT id, student_id, deck_id, deck_name, word_id, english, hungarian, mode,
                   outcome, answered_at, received_at
            FROM attempt WHERE student_id = :studentId
            ORDER BY answered_at, received_at, id
            """)
        .param("studentId", studentId)
        .query(
            (rs, rowNum) ->
                new Attempt(
                    rs.getObject("id", UUID.class),
                    rs.getLong("student_id"),
                    rs.getString("deck_id"),
                    rs.getString("deck_name"),
                    rs.getString("word_id"),
                    rs.getString("english"),
                    rs.getString("hungarian"),
                    Mode.valueOf(rs.getString("mode")),
                    Outcome.valueOf(rs.getString("outcome")),
                    rs.getObject("answered_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("received_at", OffsetDateTime.class).toInstant()))
        .list();
  }
}

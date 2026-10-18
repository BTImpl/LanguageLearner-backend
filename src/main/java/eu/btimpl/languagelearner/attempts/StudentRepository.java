package eu.btimpl.languagelearner.attempts;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The students table. */
@Repository
public class StudentRepository {

  private final JdbcClient jdbc;

  public StudentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Makes sure the student is stored and returns their id. A student that is stored already keeps
   * the id and gets the new name.
   */
  public long register(String email, String name) {
    return jdbc.sql(
            """
            INSERT INTO student (email, name) VALUES (:email, :name)
            ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name
            RETURNING id
            """)
        .param("email", normalized(email))
        .param("name", name == null ? "" : name.strip())
        .query(Long.class)
        .single();
  }

  /** Finds a student by e-mail address, ignoring case and surrounding spaces. */
  public Optional<StoredStudent> findByEmail(String email) {
    return jdbc.sql("SELECT id, email, name, created_at FROM student WHERE email = :email")
        .param("email", normalized(email))
        .query(
            (rs, rowNum) ->
                new StoredStudent(
                    rs.getLong("id"),
                    rs.getString("email"),
                    rs.getString("name"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  private static String normalized(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  static OffsetDateTime toDb(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}

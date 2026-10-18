package eu.btimpl.languagelearner.attempts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import eu.btimpl.languagelearner.sheets.Student;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Stores a student in the database the first time they are seen. Whoever was stored lately is
 * remembered for an hour, so a request does not touch the database again; the name from the sheet
 * is refreshed when that hour is over.
 */
@Service
public class StudentRegistry {

  static final Duration REMEMBER = Duration.ofHours(1);

  private final StudentRepository repository;
  private final Cache<String, Long> known;

  public StudentRegistry(StudentRepository repository, Ticker ticker) {
    this.repository = repository;
    this.known =
        Caffeine.newBuilder()
            .expireAfterWrite(REMEMBER)
            .ticker(ticker)
            .executor(Runnable::run)
            .build();
  }

  /**
   * Makes sure the student is in the database.
   *
   * @return the database id of the student
   * @throws org.springframework.dao.DataAccessException when the database cannot be used
   */
  public long register(Student student) {
    String key = student.email().strip().toLowerCase(Locale.ROOT);
    return known.get(key, email -> repository.register(email, student.name()));
  }
}

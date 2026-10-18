package eu.btimpl.languagelearner.decks;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import eu.btimpl.languagelearner.config.CacheProperties;
import eu.btimpl.languagelearner.sheets.DeckReader;
import eu.btimpl.languagelearner.sheets.SheetsException;
import eu.btimpl.languagelearner.sheets.StudentDirectory;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The decks of a student, read from the teacher's sheet. What was read is kept for a while, so the
 * sheet is not read on every request; a student can ask for a fresh read, but not too often.
 */
@Service
public class DeckService {

  private static final Logger log = LoggerFactory.getLogger(DeckService.class);

  private final DeckReader reader;
  private final StudentDirectory students;
  private final Clock clock;
  private final Cache<String, StudentDecks> decks;
  private final Cache<String, Boolean> recentRefreshes;

  public DeckService(
      DeckReader reader,
      StudentDirectory students,
      Clock clock,
      CacheProperties cacheProperties,
      Ticker ticker) {
    this.reader = reader;
    this.students = students;
    this.clock = clock;
    this.decks =
        Caffeine.newBuilder()
            .expireAfterWrite(cacheProperties.decksTtl())
            .ticker(ticker)
            .executor(Runnable::run)
            .build();
    this.recentRefreshes =
        Caffeine.newBuilder()
            .expireAfterWrite(cacheProperties.refreshInterval())
            .ticker(ticker)
            .executor(Runnable::run)
            .build();
  }

  /**
   * The decks on the student's tab, as they were when the sheet was last read. A student whose tab
   * does not exist or is empty simply has no decks yet.
   *
   * @throws SheetsException when the sheet has to be read and cannot be
   */
  public StudentDecks decksOf(String tab) {
    return decks.get(key(tab), this::read);
  }

  /**
   * Throws away what is kept for the student, the student list too, and reads the sheet again. When
   * the same student asked for this less than the refresh interval ago, nothing is read and the
   * kept decks are returned. When the sheet cannot be read but decks were kept, those are returned
   * with their old {@code updatedAt}.
   *
   * @param email the e-mail address of the student, to limit how often they can refresh
   * @param tab the tab of the student
   * @throws SheetsException when the sheet cannot be read and nothing was kept
   */
  public StudentDecks refreshOf(String email, String tab) {
    if (recentRefreshes.asMap().putIfAbsent(email.strip().toLowerCase(Locale.ROOT), true) != null) {
      return decksOf(tab);
    }
    try {
      students.refresh();
    } catch (SheetsException e) {
      log.warn("The student list could not be refreshed, the earlier one stays", e);
    }
    String key = key(tab);
    StudentDecks previous = decks.getIfPresent(key);
    try {
      StudentDecks fresh = read(key);
      decks.put(key, fresh);
      return fresh;
    } catch (SheetsException e) {
      if (previous == null) {
        throw e;
      }
      log.warn("The decks could not be refreshed, the earlier ones are returned", e);
      return previous;
    }
  }

  private static String key(String tab) {
    return tab == null ? "" : tab;
  }

  private StudentDecks read(String tab) {
    List<Deck> read;
    try {
      read = reader.readDecks(tab);
    } catch (SheetsException.TabNotFound e) {
      read = List.of();
    }
    return new StudentDecks(read, clock.instant().truncatedTo(ChronoUnit.SECONDS));
  }
}

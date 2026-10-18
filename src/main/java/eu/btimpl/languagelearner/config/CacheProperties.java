package eu.btimpl.languagelearner.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long data read from the teacher's sheet is kept, so the sheet is not read on every request. A
 * zero duration switches the cache off.
 *
 * @param studentsTtl how long the student list is kept
 * @param decksTtl how long the decks of a student are kept
 * @param refreshInterval the shortest time between two forced re-reads by the same student
 */
@ConfigurationProperties("app.cache")
public record CacheProperties(Duration studentsTtl, Duration decksTtl, Duration refreshInterval) {

  public static final Duration DEFAULT_STUDENTS_TTL = Duration.ofMinutes(5);
  public static final Duration DEFAULT_DECKS_TTL = Duration.ofMinutes(15);
  public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(10);

  public CacheProperties {
    studentsTtl = valid("students-ttl", studentsTtl, DEFAULT_STUDENTS_TTL);
    decksTtl = valid("decks-ttl", decksTtl, DEFAULT_DECKS_TTL);
    refreshInterval = valid("refresh-interval", refreshInterval, DEFAULT_REFRESH_INTERVAL);
  }

  /** The default settings. */
  public static CacheProperties defaults() {
    return new CacheProperties(null, null, null);
  }

  private static Duration valid(String name, Duration value, Duration fallback) {
    if (value == null) {
      return fallback;
    }
    if (value.isNegative()) {
      throw new IllegalArgumentException("app.cache." + name + " must not be negative: " + value);
    }
    return value;
  }
}

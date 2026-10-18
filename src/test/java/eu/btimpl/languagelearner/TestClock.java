package eu.btimpl.languagelearner;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/** A clock that only moves when a test says so; its {@link #ticker()} makes caches follow it. */
public final class TestClock extends Clock {

  public static final Instant START = Instant.parse("2026-10-07T07:12:00.789Z");

  private volatile Instant now;

  public TestClock() {
    this(START);
  }

  public TestClock(Instant start) {
    this.now = start;
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  public Ticker ticker() {
    return () -> TimeUnit.MILLISECONDS.toNanos(millis());
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}

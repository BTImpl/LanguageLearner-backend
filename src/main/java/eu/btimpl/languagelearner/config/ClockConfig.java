package eu.btimpl.languagelearner.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the app reads the time from, and the caches' ticker that follows it, so tests can
 * replace both with one clock.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  Ticker cacheTicker(Clock clock) {
    return () -> TimeUnit.MILLISECONDS.toNanos(clock.millis());
  }
}

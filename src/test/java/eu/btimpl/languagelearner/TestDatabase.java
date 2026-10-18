package eu.btimpl.languagelearner;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL in a container for the tests that start the application. Import it with
 * {@code @Import}; the app connects to it by itself and Flyway builds the schema. Needs Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:17-alpine");
  }
}

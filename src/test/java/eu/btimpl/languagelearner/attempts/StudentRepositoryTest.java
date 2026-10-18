package eu.btimpl.languagelearner.attempts;

import static org.assertj.core.api.Assertions.assertThat;

import eu.btimpl.languagelearner.TestDatabase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The students table on a real PostgreSQL, built by the migrations. */
@SpringBootTest
@Import(TestDatabase.class)
class StudentRepositoryTest {

  @Autowired private StudentRepository students;

  @Test
  void registersANewStudent() {
    long id = students.register("new.student@example.com", "New Student");

    StoredStudent stored = students.findByEmail("new.student@example.com").orElseThrow();
    assertThat(stored.id()).isEqualTo(id);
    assertThat(stored.name()).isEqualTo("New Student");
    assertThat(stored.createdAt())
        .isBetween(Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plusSeconds(5));
  }

  @Test
  void theSameStudentKeepsTheirIdAndGetsTheNewName() {
    long first = students.register("repeat@example.com", "Old Name");
    Instant createdAt = students.findByEmail("repeat@example.com").orElseThrow().createdAt();

    long second = students.register("repeat@example.com", "New Name");

    StoredStudent stored = students.findByEmail("repeat@example.com").orElseThrow();
    assertThat(second).isEqualTo(first);
    assertThat(stored.name()).isEqualTo("New Name");
    assertThat(stored.createdAt()).isEqualTo(createdAt);
  }

  @Test
  void theAddressIsStoredInLowerCaseWithoutSpaces() {
    long id = students.register("  Mixed.Case@Example.COM ", "Mixed");

    assertThat(students.register("mixed.case@example.com", "Mixed")).isEqualTo(id);
    assertThat(students.findByEmail("MIXED.case@example.com ").orElseThrow().email())
        .isEqualTo("mixed.case@example.com");
  }

  @Test
  void aMissingNameIsStoredAsEmpty() {
    students.register("noname@example.com", null);

    assertThat(students.findByEmail("noname@example.com").orElseThrow().name()).isEmpty();
  }

  @Test
  void anUnknownStudentIsNotFound() {
    assertThat(students.findByEmail("nobody-at-all@example.com")).isEmpty();
  }
}

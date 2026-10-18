package eu.btimpl.languagelearner.attempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.btimpl.languagelearner.TestClock;
import eu.btimpl.languagelearner.sheets.Student;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class StudentRegistryTest {

  private final TestClock clock = new TestClock();
  private final StudentRepository repository = mock(StudentRepository.class);
  private final StudentRegistry registry = new StudentRegistry(repository, clock.ticker());

  @Test
  void aStudentIsStoredOnceAndRememberedForAnHour() {
    when(repository.register("tomi@example.com", "Tomi")).thenReturn(7L);
    Student tomi = new Student("Tomi@Example.com ", "Tomi", "Tomi");

    assertThat(registry.register(tomi)).isEqualTo(7L);
    clock.advance(Duration.ofMinutes(59));
    assertThat(registry.register(tomi)).isEqualTo(7L);

    verify(repository, times(1)).register("tomi@example.com", "Tomi");

    clock.advance(Duration.ofMinutes(2));
    registry.register(tomi);

    verify(repository, times(2)).register("tomi@example.com", "Tomi");
  }

  @Test
  void aFailureIsNotRemembered() {
    when(repository.register("tomi@example.com", "Tomi"))
        .thenThrow(new DataAccessResourceFailureException("down"))
        .thenReturn(7L);
    Student tomi = new Student("tomi@example.com", "Tomi", "Tomi");

    assertThatThrownBy(() -> registry.register(tomi))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(registry.register(tomi)).isEqualTo(7L);
  }
}

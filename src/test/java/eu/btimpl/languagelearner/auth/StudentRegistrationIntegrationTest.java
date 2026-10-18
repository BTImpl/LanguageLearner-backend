package eu.btimpl.languagelearner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import eu.btimpl.languagelearner.TestDatabase;
import eu.btimpl.languagelearner.attempts.StudentRegistry;
import eu.btimpl.languagelearner.attempts.StudentRepository;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** A student is stored in the database when first seen, and a broken database does not stop it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthTestSupport.Config.class, TestDatabase.class})
@TestPropertySource(properties = {"app.cache.students-ttl=0s", "app.cache.decks-ttl=0s"})
class StudentRegistrationIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;
  @MockitoSpyBean private StudentRegistry registry;
  @Autowired private StudentRepository students;

  @BeforeEach
  void theSheetListsTheStudents() {
    when(sheetsClient.readTab("Students"))
        .thenReturn(
            List.of(
                List.of("email", "name", "tab"),
                List.of("first.timer@example.com", "First Timer", "First"),
                List.of("down.db@example.com", "Down Db", "Down")));
  }

  private HttpResponse<String> getMe(String email) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
            .header("Authorization", "Bearer " + AuthTestSupport.tokenOf(email))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void aStudentIsStoredAtTheFirstAuthenticatedRequest() throws Exception {
    assertThat(students.findByEmail("first.timer@example.com")).isEmpty();

    HttpResponse<String> response = getMe("first.timer@example.com");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(students.findByEmail("first.timer@example.com"))
        .hasValueSatisfying(stored -> assertThat(stored.name()).isEqualTo("First Timer"));
  }

  @Test
  void aStudentNotOnTheListIsNotStored() throws Exception {
    HttpResponse<String> response = getMe("stranger@example.com");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(students.findByEmail("stranger@example.com")).isEmpty();
  }

  @Test
  void aBrokenDatabaseDoesNotStopTheRequest() throws Exception {
    doThrow(new DataAccessResourceFailureException("database is down"))
        .when(registry)
        .register(any());

    HttpResponse<String> response = getMe("down.db@example.com");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"email\":\"down.db@example.com\"");
  }
}

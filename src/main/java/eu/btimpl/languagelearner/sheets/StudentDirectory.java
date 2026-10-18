package eu.btimpl.languagelearner.sheets;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import eu.btimpl.languagelearner.config.CacheProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The students of the teacher's sheet: tab {@code Students} with columns {@code email | name |
 * tab}. The list is kept for a few minutes, so the sheet is not read on every request.
 */
@Component
public class StudentDirectory {

  private static final String EMAIL = "email";
  private static final String NAME = "name";
  private static final String TAB = "tab";

  private static final String LIST = "students";

  private final SheetsClient client;
  private final SheetsProperties properties;
  private final Cache<String, List<Student>> cache;

  public StudentDirectory(
      SheetsClient client,
      SheetsProperties properties,
      CacheProperties cacheProperties,
      Ticker ticker) {
    this.client = client;
    this.properties = properties;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(cacheProperties.studentsTtl())
            .ticker(ticker)
            .executor(Runnable::run)
            .build();
  }

  /**
   * Finds a student by e-mail address, ignoring case and surrounding spaces. When an address is
   * listed twice, the first row wins.
   *
   * @throws SheetsException when the sheet cannot be read
   */
  public Optional<Student> findByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    String wanted = email.strip().toLowerCase(Locale.ROOT);
    return students().stream()
        .filter(student -> student.email().toLowerCase(Locale.ROOT).equals(wanted))
        .findFirst();
  }

  /**
   * All students in the order of the sheet. Rows without an e-mail address are left out. The list
   * may be a few minutes old.
   *
   * @throws SheetsException when the sheet cannot be read
   */
  public List<Student> students() {
    return cache.get(LIST, key -> read());
  }

  /**
   * Reads the list from the sheet again. When that fails the list kept so far stays as it was.
   *
   * @throws SheetsException when the sheet cannot be read
   */
  public void refresh() {
    cache.put(LIST, read());
  }

  private List<Student> read() {
    String tab = properties.studentsTab();
    SheetTable table = SheetTable.parse(tab, client.readTab(tab), List.of(EMAIL, NAME, TAB));
    List<Student> students = new ArrayList<>();
    for (SheetTable.Row row : table.rows()) {
      String email = row.get(EMAIL);
      if (!email.isEmpty()) {
        students.add(new Student(email, row.get(NAME), row.get(TAB)));
      }
    }
    return List.copyOf(students);
  }
}

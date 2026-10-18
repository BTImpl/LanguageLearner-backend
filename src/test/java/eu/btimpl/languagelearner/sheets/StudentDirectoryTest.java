package eu.btimpl.languagelearner.sheets;

import static eu.btimpl.languagelearner.sheets.FakeSheetsClient.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StudentDirectoryTest {

  private static StudentDirectory directory(FakeSheetsClient client) {
    return new StudentDirectory(client, new SheetsProperties("sheet-id", "Students"));
  }

  @Test
  void findsAStudentIgnoringCaseAndSpaces() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Students",
                row("email", "name", "tab"),
                row("Anna.Kiss@Example.com ", " Kiss Anna ", " Anna "),
                row("tomi@example.com", "Tomi", "Tomi"));

    assertThat(directory(client).findByEmail("  anna.kiss@example.COM"))
        .contains(new Student("Anna.Kiss@Example.com", "Kiss Anna", "Anna"));
  }

  @Test
  void unknownOrBlankAddressFindsNobody() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Students", row("email", "name", "tab"), row("tomi@example.com", "Tomi", "Tomi"));
    StudentDirectory directory = directory(client);

    assertThat(directory.findByEmail("nobody@example.com")).isEmpty();
    assertThat(directory.findByEmail("")).isEmpty();
    assertThat(directory.findByEmail(null)).isEmpty();
  }

  @Test
  void columnsAreFoundByHeaderNotByPosition() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Students",
                row("Tab", "Email", "Name"),
                row("Tomi", "tomi@example.com", "Bodis Tamas"));

    assertThat(directory(client).findByEmail("tomi@example.com"))
        .contains(new Student("tomi@example.com", "Bodis Tamas", "Tomi"));
  }

  @Test
  void skipsBlankRowsAndRowsWithoutEmail() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Students",
                row("email", "name", "tab"),
                row(),
                row("", "No Email", "X"),
                row("   ", "", ""),
                row("tomi@example.com", "Tomi", "Tomi"));

    assertThat(directory(client).students())
        .containsExactly(new Student("tomi@example.com", "Tomi", "Tomi"));
  }

  @Test
  void shortRowsReadAsEmptyCells() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Students", row("email", "name", "tab"), row("tomi@example.com"));

    assertThat(directory(client).students())
        .containsExactly(new Student("tomi@example.com", "", ""));
  }

  @Test
  void theFirstRowWinsForARepeatedAddress() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Students",
                row("email", "name", "tab"),
                row("tomi@example.com", "First", "A"),
                row("TOMI@example.com", "Second", "B"));

    assertThat(directory(client).findByEmail("tomi@example.com"))
        .contains(new Student("tomi@example.com", "First", "A"));
  }

  @Test
  void anEmptyTabHasNoStudents() {
    FakeSheetsClient client = new FakeSheetsClient().tab("Students");

    assertThat(directory(client).students()).isEmpty();
  }

  @Test
  void aMissingColumnIsALayoutError() {
    FakeSheetsClient client =
        new FakeSheetsClient().tab("Students", row("email", "name"), row("tomi@example.com", "T"));

    assertThatThrownBy(() -> directory(client).students())
        .isInstanceOf(SheetsException.InvalidLayout.class)
        .hasMessageContaining("Students")
        .hasMessageContaining("tab");
  }

  @Test
  void usesTheConfiguredTab() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Diákok", row("email", "name", "tab"), row("tomi@example.com", "Tomi", "Tomi"));
    StudentDirectory directory =
        new StudentDirectory(client, new SheetsProperties("sheet-id", "Diákok"));

    assertThat(directory.findByEmail("tomi@example.com")).isPresent();
  }

  @Test
  void sheetErrorsPassThrough() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .failing("Students", new SheetsException.AccessDenied("no access", null));

    assertThatThrownBy(() -> directory(client).findByEmail("tomi@example.com"))
        .isInstanceOf(SheetsException.AccessDenied.class);
  }

  @Test
  void theDefaultTabIsStudents() {
    assertThat(new SheetsProperties(null, null))
        .isEqualTo(new SheetsProperties("", SheetsProperties.DEFAULT_STUDENTS_TAB));
  }
}

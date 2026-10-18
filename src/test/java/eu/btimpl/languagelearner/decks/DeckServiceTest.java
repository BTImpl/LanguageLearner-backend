package eu.btimpl.languagelearner.decks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.btimpl.languagelearner.sheets.DeckReader;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeckServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T07:12:00.789Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private static List<String> row(String... cells) {
    return Arrays.asList(cells);
  }

  private DeckService service(SheetsClient client) {
    return new DeckService(new DeckReader(client), clock);
  }

  @Test
  void readsTheDecksOfATabAndNotesWhenItDidSo() {
    SheetsClient client =
        tab ->
            List.of(
                row("english", "hungarian", "deck"),
                row("ticket", "jegy", "Travel"),
                row("apple", "alma", "Food"));

    StudentDecks result = service(client).decksOf("Tomi");

    assertThat(result.decks()).extracting(Deck::name).containsExactly("Food", "Travel");
    assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-10-07T07:12:00Z"));
  }

  @Test
  void aStudentWhoseTabDoesNotExistHasNoDecks() {
    SheetsClient client =
        tab -> {
          throw new SheetsException.TabNotFound(tab, null);
        };

    StudentDecks result = service(client).decksOf("Nobody");

    assertThat(result.decks()).isEmpty();
    assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-10-07T07:12:00Z"));
  }

  @Test
  void aStudentWithoutATabHasNoDecks() {
    SheetsClient client =
        tab -> {
          throw new AssertionError("the sheet must not be read without a tab");
        };

    assertThat(service(client).decksOf("").decks()).isEmpty();
    assertThat(service(client).decksOf(null).decks()).isEmpty();
  }

  @Test
  void anEmptyTabHasNoDecks() {
    SheetsClient client = tab -> List.of();

    assertThat(service(client).decksOf("Tomi").decks()).isEmpty();
  }

  @Test
  void otherSheetErrorsAreNotHidden() {
    SheetsClient client =
        tab -> {
          throw new SheetsException.Unavailable("down", null);
        };

    assertThatThrownBy(() -> service(client).decksOf("Tomi"))
        .isInstanceOf(SheetsException.Unavailable.class);
  }
}

package eu.btimpl.languagelearner.decks;

import static eu.btimpl.languagelearner.sheets.FakeSheetsClient.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.btimpl.languagelearner.TestClock;
import eu.btimpl.languagelearner.config.CacheProperties;
import eu.btimpl.languagelearner.sheets.DeckReader;
import eu.btimpl.languagelearner.sheets.FakeSheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import eu.btimpl.languagelearner.sheets.SheetsProperties;
import eu.btimpl.languagelearner.sheets.StudentDirectory;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeckServiceTest {

  private static final Instant START = Instant.parse("2026-10-07T07:12:00Z");

  private final TestClock clock = new TestClock();
  private final FakeSheetsClient sheet =
      new FakeSheetsClient()
          .tab(
              "Students",
              row("email", "name", "tab"),
              row("tomi@example.com", "Tomi", "Tomi"),
              row("anna@example.com", "Anna", "Anna"))
          .tab(
              "Tomi",
              row("english", "hungarian", "deck"),
              row("ticket", "jegy", "Travel"),
              row("apple", "alma", "Food"))
          .tab("Anna", row("english", "hungarian", "deck"), row("dog", "kutya", "Animals"));

  private DeckService service() {
    return service(CacheProperties.defaults());
  }

  private DeckService service(CacheProperties cache) {
    StudentDirectory students =
        new StudentDirectory(
            sheet, new SheetsProperties("sheet-id", "Students"), cache, clock.ticker());
    return new DeckService(new DeckReader(sheet), students, clock, cache, clock.ticker());
  }

  @Test
  void readsTheDecksOfATabAndNotesWhenItDidSo() {
    StudentDecks result = service().decksOf("Tomi");

    assertThat(result.decks()).extracting(Deck::name).containsExactly("Food", "Travel");
    assertThat(result.updatedAt()).isEqualTo(START);
  }

  @Test
  void aStudentWhoseTabDoesNotExistHasNoDecks() {
    StudentDecks result = service().decksOf("Nobody");

    assertThat(result.decks()).isEmpty();
    assertThat(result.updatedAt()).isEqualTo(START);
  }

  @Test
  void aStudentWithoutATabHasNoDecksAndTheSheetIsNotReadForIt() {
    DeckService service = service();

    assertThat(service.decksOf("").decks()).isEmpty();
    assertThat(service.decksOf(null).decks()).isEmpty();
    assertThat(sheet.reads()).isZero();
  }

  @Test
  void anEmptyTabHasNoDecks() {
    sheet.tab("Empty");

    assertThat(service().decksOf("Empty").decks()).isEmpty();
  }

  @Test
  void otherSheetErrorsAreNotHidden() {
    sheet.failing("Tomi", new SheetsException.Unavailable("down", null));

    assertThatThrownBy(() -> service().decksOf("Tomi"))
        .isInstanceOf(SheetsException.Unavailable.class);
  }

  @Test
  void twoReadsInARowReadTheSheetOnce() {
    DeckService service = service();

    StudentDecks first = service.decksOf("Tomi");
    clock.advance(Duration.ofMinutes(14));
    StudentDecks second = service.decksOf("Tomi");

    assertThat(sheet.reads()).isEqualTo(1);
    assertThat(second).isEqualTo(first);
    assertThat(second.updatedAt()).isEqualTo(START);
  }

  @Test
  void theDecksAreKeptPerTab() {
    DeckService service = service();

    service.decksOf("Tomi");
    StudentDecks annas = service.decksOf("Anna");

    assertThat(sheet.reads()).isEqualTo(2);
    assertThat(annas.decks()).extracting(Deck::name).containsExactly("Animals");
  }

  @Test
  void afterFifteenMinutesTheSheetIsReadAgain() {
    DeckService service = service();
    service.decksOf("Tomi");

    clock.advance(Duration.ofMinutes(16));
    StudentDecks later = service.decksOf("Tomi");

    assertThat(sheet.reads()).isEqualTo(2);
    assertThat(later.updatedAt()).isEqualTo(START.plus(Duration.ofMinutes(16)));
  }

  @Test
  void theDurationsCanBeConfigured() {
    DeckService service =
        service(
            new CacheProperties(
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofSeconds(1)));
    service.decksOf("Tomi");

    clock.advance(Duration.ofMinutes(1));
    service.decksOf("Tomi");
    assertThat(sheet.reads()).isEqualTo(1);

    clock.advance(Duration.ofMinutes(2));
    service.decksOf("Tomi");
    assertThat(sheet.reads()).isEqualTo(2);
  }

  @Test
  void aZeroDurationSwitchesTheCacheOff() {
    DeckService service = service(new CacheProperties(Duration.ZERO, Duration.ZERO, Duration.ZERO));

    service.decksOf("Tomi");
    service.decksOf("Tomi");

    assertThat(sheet.reads()).isEqualTo(2);
  }

  @Test
  void aFailedReadIsNotKept() {
    DeckService service = service();
    sheet.failing("Tomi", new SheetsException.Unavailable("down", null));
    assertThatThrownBy(() -> service.decksOf("Tomi"))
        .isInstanceOf(SheetsException.Unavailable.class);

    sheet.working("Tomi");

    assertThat(service.decksOf("Tomi").decks()).hasSize(2);
  }

  @Test
  void refreshReadsTheSheetAgainAndKeepsTheNewDecks() {
    DeckService service = service();
    service.decksOf("Tomi");
    sheet.tab("Tomi", row("english", "hungarian", "deck"), row("cat", "macska", "Animals"));
    clock.advance(Duration.ofMinutes(1));

    StudentDecks refreshed = service.refreshOf("tomi@example.com", "Tomi");

    assertThat(refreshed.decks()).extracting(Deck::name).containsExactly("Animals");
    assertThat(refreshed.updatedAt()).isEqualTo(START.plus(Duration.ofMinutes(1)));
    assertThat(service.decksOf("Tomi")).isEqualTo(refreshed);
  }

  @Test
  void refreshReadsTheStudentListAgainToo() {
    DeckService service = service();

    service.refreshOf("tomi@example.com", "Tomi");

    // The decks of Tomi and the Students tab.
    assertThat(sheet.reads()).isEqualTo(2);
  }

  @Test
  void aStudentCanRefreshOnlyOnceInTenSeconds() {
    DeckService service = service();
    StudentDecks first = service.refreshOf("tomi@example.com", "Tomi");
    int readsAfterFirst = sheet.reads();
    sheet.tab("Tomi", row("english", "hungarian", "deck"), row("cat", "macska", "Animals"));
    clock.advance(Duration.ofSeconds(9));

    StudentDecks tooSoon = service.refreshOf("TOMI@example.com ", "Tomi");

    assertThat(tooSoon).isEqualTo(first);
    assertThat(sheet.reads()).isEqualTo(readsAfterFirst);

    clock.advance(Duration.ofSeconds(2));
    StudentDecks later = service.refreshOf("tomi@example.com", "Tomi");

    assertThat(later.decks()).extracting(Deck::name).containsExactly("Animals");
  }

  @Test
  void theLimitIsPerStudent() {
    DeckService service = service();
    service.refreshOf("tomi@example.com", "Tomi");

    StudentDecks annas = service.refreshOf("anna@example.com", "Anna");

    assertThat(annas.decks()).extracting(Deck::name).containsExactly("Animals");
    assertThat(annas.updatedAt()).isEqualTo(START);
  }

  @Test
  void aRefreshThatIsTooSoonReadsADeckThatWasNotKeptYet() {
    DeckService service = service();
    service.refreshOf("tomi@example.com", "Tomi");
    clock.advance(Duration.ofSeconds(1));

    StudentDecks annas = service.refreshOf("tomi@example.com", "Anna");

    assertThat(annas.decks()).extracting(Deck::name).containsExactly("Animals");
  }

  @Test
  void whenTheSheetCannotBeReadTheEarlierDecksComeBackWithTheirOldTime() {
    DeckService service = service();
    StudentDecks earlier = service.decksOf("Tomi");
    clock.advance(Duration.ofMinutes(1));
    sheet.failing("Tomi", new SheetsException.Unavailable("down", null));
    sheet.failing("Students", new SheetsException.Unavailable("down", null));

    StudentDecks refreshed = service.refreshOf("tomi@example.com", "Tomi");

    assertThat(refreshed).isEqualTo(earlier);
    assertThat(refreshed.updatedAt()).isEqualTo(START);
    assertThat(service.decksOf("Tomi")).isEqualTo(earlier);
  }

  @Test
  void whenTheSheetCannotBeReadAndNothingWasKeptTheRefreshFails() {
    DeckService service = service();
    sheet.failing("Tomi", new SheetsException.Unavailable("down", null));

    assertThatThrownBy(() -> service.refreshOf("tomi@example.com", "Tomi"))
        .isInstanceOf(SheetsException.Unavailable.class);
  }
}

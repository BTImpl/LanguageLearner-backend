package eu.btimpl.languagelearner.sheets;

import static eu.btimpl.languagelearner.sheets.FakeSheetsClient.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.btimpl.languagelearner.decks.Deck;
import eu.btimpl.languagelearner.decks.Word;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeckReaderTest {

  private static final List<String> HEADER = row("english", "hungarian", "deck", "example");

  private static DeckReader reader(FakeSheetsClient client) {
    return new DeckReader(client);
  }

  private static List<String> englishOf(Deck deck) {
    return deck.words().stream().map(Word::english).toList();
  }

  @Test
  void groupsWordsIntoDecksByTheDeckColumn() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("apple", "alma", "Food"),
                row("passport", "útlevél", "Travel"),
                row("bread", "kenyér", "Food"),
                row("airport", "repülőtér", "Travel"));

    List<Deck> decks = reader(client).readDecks("Tomi");

    assertThat(decks).extracting(Deck::name).containsExactly("Food", "Travel");
    assertThat(englishOf(decks.get(0))).containsExactly("apple", "bread");
    assertThat(englishOf(decks.get(1))).containsExactly("ticket", "passport", "airport");
  }

  @Test
  void readsTheRealSheetShapeWhereEmptyTrailingCellsAreMissing() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("boarding pass", "beszállókártya", "Travel"));

    Deck deck = reader(client).readDecks("Tomi").get(0);

    assertThat(deck.name()).isEqualTo("Travel");
    assertThat(deck.words()).hasSize(2);
    assertThat(deck.words().get(1).english()).isEqualTo("boarding pass");
    assertThat(deck.words().get(1).example()).isNull();
  }

  @Test
  void skipsBlankAndIncompleteRows() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row(),
                row("", "", ""),
                row("only english", "", "Travel"),
                row("", "csak magyar", "Travel"),
                row("  ", "x", "Travel"),
                row(";", "y", "Travel"),
                row("passport", "útlevél", "Travel"));

    assertThat(englishOf(reader(client).readDecks("Tomi").get(0)))
        .containsExactly("ticket", "passport");
  }

  @Test
  void trimsTheEdgesOfCells() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Tomi", HEADER, row("  ticket ", " jegy  ", "  Travel ", "  one way  "));

    Deck deck = reader(client).readDecks("Tomi").get(0);

    assertThat(deck.name()).isEqualTo("Travel");
    Word word = deck.words().get(0);
    assertThat(word.english()).isEqualTo("ticket");
    assertThat(word.hungarian()).isEqualTo("jegy");
    assertThat(word.example()).isEqualTo("one way");
  }

  @Test
  void semicolonSeparatedVariantsAreAcceptedAndTheFirstIsShown() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Tomi", HEADER, row("luggage; baggage ;; luggage", "poggyász; csomag", "Travel"));

    Word word = reader(client).readDecks("Tomi").get(0).words().get(0);

    assertThat(word.english()).isEqualTo("luggage");
    assertThat(word.acceptedEnglish()).containsExactly("luggage", "baggage");
    assertThat(word.hungarian()).isEqualTo("poggyász");
    assertThat(word.acceptedHungarian()).containsExactly("poggyász", "csomag");
  }

  @Test
  void anEmptyDeckCellMeansTheDefaultDeck() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Tomi", HEADER, row("ticket", "jegy"), row("passport", "útlevél", " "));

    List<Deck> decks = reader(client).readDecks("Tomi");

    assertThat(decks).hasSize(1);
    assertThat(decks.get(0).name()).isEqualTo("Szavak");
    assertThat(decks.get(0).words()).hasSize(2);
  }

  @Test
  void deckNamesThatDifferOnlyInCaseOrSpacesAreOneDeck() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("passport", "útlevél", " travel "),
                row("airport", "repülőtér", "TRAVEL"));

    List<Deck> decks = reader(client).readDecks("Tomi");

    assertThat(decks).hasSize(1);
    assertThat(decks.get(0).name()).isEqualTo("Travel");
    assertThat(decks.get(0).words()).hasSize(3);
  }

  @Test
  void decksAreSortedByNameIgnoringCase() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("a", "a", "Zebra"),
                row("b", "b", "alma"),
                row("c", "c", "Béka"));

    assertThat(reader(client).readDecks("Tomi"))
        .extracting(Deck::name)
        .containsExactly("alma", "Béka", "Zebra");
  }

  @Test
  void idsAreStableAcrossReads() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("passport", "útlevél", "Travel"));
    DeckReader reader = reader(client);

    Deck first = reader.readDecks("Tomi").get(0);
    Deck second = reader.readDecks("Tomi").get(0);

    assertThat(second).isEqualTo(first);
    assertThat(first.id()).hasSize(16).matches("[0-9a-f]+");
    assertThat(first.words()).extracting(Word::id).doesNotHaveDuplicates();
  }

  @Test
  void theSameWordsInAnotherStudentsTabGetOtherIds() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("passport", "útlevél", "Travel"))
            .tab(
                "Anna",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("passport", "útlevél", "Travel"));
    DeckReader reader = reader(client);

    Deck tomi = reader.readDecks("Tomi").get(0);
    Deck anna = reader.readDecks("Anna").get(0);

    assertThat(anna.id()).isNotEqualTo(tomi.id());
    assertThat(anna.words().get(0).id()).isNotEqualTo(tomi.words().get(0).id());
  }

  @Test
  void wordIdsDoNotChangeWhenTheRowsAreReordered() {
    FakeSheetsClient before =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("ticket", "jegy", "Travel"),
                row("passport", "útlevél", "Travel"));
    FakeSheetsClient after =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("passport", "útlevél", "Travel"),
                row("ticket", "jegy", "Travel"),
                row("map", "térkép", "Travel"));

    Deck first = reader(before).readDecks("Tomi").get(0);
    Deck second = reader(after).readDecks("Tomi").get(0);

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.words().get(1).id()).isEqualTo(first.words().get(0).id());
    assertThat(second.words().get(0).id()).isEqualTo(first.words().get(1).id());
  }

  @Test
  void aRepeatedEnglishWordInOneDeckGetsAnotherId() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab(
                "Tomi",
                HEADER,
                row("bank", "bank", "Words"),
                row("bank", "part", "Words"),
                row("Bank", "pad", "Words"));

    List<Word> words = reader(client).readDecks("Tomi").get(0).words();

    assertThat(words).extracting(Word::id).doesNotHaveDuplicates();
  }

  @Test
  void columnsAreFoundByHeaderNotByPosition() {
    FakeSheetsClient client =
        new FakeSheetsClient()
            .tab("Tomi", row("Deck", "Hungarian", "English"), row("Travel", "jegy", "ticket"));

    Word word = reader(client).readDecks("Tomi").get(0).words().get(0);

    assertThat(word.english()).isEqualTo("ticket");
    assertThat(word.hungarian()).isEqualTo("jegy");
  }

  @Test
  void theDeckAndExampleColumnsAreOptional() {
    FakeSheetsClient client =
        new FakeSheetsClient().tab("Tomi", row("english", "hungarian"), row("ticket", "jegy"));

    List<Deck> decks = reader(client).readDecks("Tomi");

    assertThat(decks).extracting(Deck::name).containsExactly("Szavak");
    assertThat(decks.get(0).words().get(0).example()).isNull();
  }

  @Test
  void aMissingRequiredColumnIsALayoutError() {
    FakeSheetsClient client =
        new FakeSheetsClient().tab("Tomi", row("english", "deck"), row("ticket", "Travel"));

    assertThatThrownBy(() -> reader(client).readDecks("Tomi"))
        .isInstanceOf(SheetsException.InvalidLayout.class)
        .hasMessageContaining("hungarian");
  }

  @Test
  void anEmptyTabHasNoDecks() {
    FakeSheetsClient client = new FakeSheetsClient().tab("Tomi");

    assertThat(reader(client).readDecks("Tomi")).isEmpty();
  }

  @Test
  void aTabWithOnlyAHeaderHasNoDecks() {
    FakeSheetsClient client = new FakeSheetsClient().tab("Tomi", HEADER);

    assertThat(reader(client).readDecks("Tomi")).isEmpty();
  }

  @Test
  void aBlankTabNameGivesNoDecksAndDoesNotTouchTheSheet() {
    FakeSheetsClient client = new FakeSheetsClient();
    DeckReader reader = reader(client);

    assertThat(reader.readDecks("")).isEmpty();
    assertThat(reader.readDecks("  ")).isEmpty();
    assertThat(reader.readDecks(null)).isEmpty();
    assertThat(client.reads()).isZero();
  }

  @Test
  void aMissingTabIsReportedAsTabNotFound() {
    FakeSheetsClient client = new FakeSheetsClient();

    assertThatThrownBy(() -> reader(client).readDecks("Nobody"))
        .isInstanceOf(SheetsException.TabNotFound.class)
        .hasMessageContaining("Nobody");
  }

  @Test
  void sheetErrorsPassThrough() {
    FakeSheetsClient client =
        new FakeSheetsClient().failing("Tomi", new SheetsException.QuotaExceeded(null));

    assertThatThrownBy(() -> reader(client).readDecks("Tomi"))
        .isInstanceOf(SheetsException.QuotaExceeded.class);
  }
}

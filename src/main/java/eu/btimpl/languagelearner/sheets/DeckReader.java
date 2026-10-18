package eu.btimpl.languagelearner.sheets;

import eu.btimpl.languagelearner.decks.Deck;
import eu.btimpl.languagelearner.decks.Word;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads the words of one student's tab ({@code english | hungarian | deck | example}) and groups
 * them into decks by the {@code deck} column.
 *
 * <p>Rows without an English or a Hungarian word are skipped, blank {@code deck} cells mean the
 * deck {@value #DEFAULT_DECK_NAME}. A cell may hold several accepted variants separated by {@code
 * ;}; the first is the one shown. Ids are hashes of the tab, deck and word names, so they stay the
 * same when the sheet is read again.
 */
@Component
public class DeckReader {

  /** Name of the deck for words whose {@code deck} cell is empty. */
  public static final String DEFAULT_DECK_NAME = "Szavak";

  private static final String ENGLISH = "english";
  private static final String HUNGARIAN = "hungarian";
  private static final String DECK = "deck";
  private static final String EXAMPLE = "example";

  private final SheetsClient client;

  public DeckReader(SheetsClient client) {
    this.client = client;
  }

  /**
   * Reads the decks of a tab. The decks come sorted by name (Hungarian collation, case ignored),
   * the words of a deck in the order of the sheet. A blank tab name or an empty tab gives no decks.
   *
   * @throws SheetsException when the tab cannot be read, for example {@link
   *     SheetsException.TabNotFound}
   */
  public List<Deck> readDecks(String tab) {
    if (tab == null || tab.isBlank()) {
      return List.of();
    }
    SheetTable table = SheetTable.parse(tab, client.readTab(tab), List.of(ENGLISH, HUNGARIAN));
    String tabKey = Names.normalize(tab);
    Map<String, DeckBuilder> builders = new LinkedHashMap<>();
    for (SheetTable.Row row : table.rows()) {
      List<String> english = variants(row.get(ENGLISH));
      List<String> hungarian = variants(row.get(HUNGARIAN));
      if (english.isEmpty() || hungarian.isEmpty()) {
        continue;
      }
      String deckCell = row.get(DECK);
      String deckName = deckCell.isEmpty() ? DEFAULT_DECK_NAME : deckCell;
      String deckKey = Names.normalize(deckName);
      builders
          .computeIfAbsent(deckKey, key -> new DeckBuilder(deckName, Ids.of(tabKey, key)))
          .add(english, hungarian, row.get(EXAMPLE));
    }
    Collator collator = Collator.getInstance(Locale.forLanguageTag("hu"));
    return builders.values().stream()
        .map(DeckBuilder::build)
        .sorted(Comparator.comparing(Deck::name, collator))
        .toList();
  }

  /** Splits a cell at {@code ;}, trims the parts and drops the empty and repeated ones. */
  static List<String> variants(String cell) {
    return Arrays.stream(cell.split(";"))
        .map(String::strip)
        .filter(variant -> !variant.isEmpty())
        .distinct()
        .toList();
  }

  /** Collects the words of one deck. */
  private static final class DeckBuilder {

    private final String name;
    private final String id;
    private final List<Word> words = new ArrayList<>();
    private final Map<String, Integer> seen = new HashMap<>();

    DeckBuilder(String name, String id) {
      this.name = name;
      this.id = id;
    }

    void add(List<String> english, List<String> hungarian, String example) {
      // The same English word twice in a deck must not share an id: the repeat gets a counter.
      String key = Names.normalize(english.get(0));
      int occurrence = seen.merge(key, 1, Integer::sum);
      String wordId = Ids.of(id, occurrence == 1 ? key : key + "#" + occurrence);
      words.add(
          new Word(
              wordId,
              english.get(0),
              hungarian.get(0),
              english,
              hungarian,
              example.isEmpty() ? null : example));
    }

    Deck build() {
      return new Deck(id, name, words);
    }
  }
}

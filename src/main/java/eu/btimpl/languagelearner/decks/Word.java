package eu.btimpl.languagelearner.decks;

import java.util.List;

/**
 * One word of a deck.
 *
 * <p>{@code english} and {@code hungarian} are the forms shown to the student (the first variant in
 * the sheet cell); the accepted lists hold every variant, the shown one included. {@code example}
 * is {@code null} when the sheet has none.
 */
public record Word(
    String id,
    String english,
    String hungarian,
    List<String> acceptedEnglish,
    List<String> acceptedHungarian,
    String example) {

  public Word {
    acceptedEnglish = List.copyOf(acceptedEnglish);
    acceptedHungarian = List.copyOf(acceptedHungarian);
  }
}

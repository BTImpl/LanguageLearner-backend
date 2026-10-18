package eu.btimpl.languagelearner.decks;

import java.util.List;

/** A named group of words of one student. The words keep the order of the sheet. */
public record Deck(String id, String name, List<Word> words) {

  public Deck {
    words = List.copyOf(words);
  }
}

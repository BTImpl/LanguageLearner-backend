package eu.btimpl.languagelearner.sheets;

import java.text.Normalizer;
import java.util.Locale;

/** Normalisation of names typed into the sheet, so that small typing differences do not matter. */
final class Names {

  private Names() {}

  /** Trims, collapses runs of whitespace, unifies the Unicode form and lowercases. */
  static String normalize(String name) {
    if (name == null) {
      return "";
    }
    return Normalizer.normalize(name, Normalizer.Form.NFC)
        .strip()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }
}

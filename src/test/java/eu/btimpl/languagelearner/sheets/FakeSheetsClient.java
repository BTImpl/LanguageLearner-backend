package eu.btimpl.languagelearner.sheets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A spreadsheet in memory: tabs with rows, and tabs that fail. */
final class FakeSheetsClient implements SheetsClient {

  private final Map<String, List<List<String>>> tabs = new HashMap<>();
  private final Map<String, SheetsException> failures = new HashMap<>();
  private int reads;

  /** One row; give fewer cells than the header to mimic the Sheets API dropping empty ones. */
  static List<String> row(String... cells) {
    return Arrays.asList(cells);
  }

  @SafeVarargs
  final FakeSheetsClient tab(String name, List<String>... rows) {
    tabs.put(name, List.of(rows));
    return this;
  }

  FakeSheetsClient failing(String name, SheetsException failure) {
    failures.put(name, failure);
    return this;
  }

  int reads() {
    return reads;
  }

  @Override
  public List<List<String>> readTab(String tab) {
    reads++;
    SheetsException failure = failures.get(tab);
    if (failure != null) {
      throw failure;
    }
    List<List<String>> rows = tabs.get(tab);
    if (rows == null) {
      throw new SheetsException.TabNotFound(tab, null);
    }
    return rows;
  }
}

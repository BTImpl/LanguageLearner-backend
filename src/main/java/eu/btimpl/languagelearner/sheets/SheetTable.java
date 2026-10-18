package eu.btimpl.languagelearner.sheets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A tab read as a table: the first row names the columns, and columns are found by that name
 * (case-insensitive), not by position.
 */
final class SheetTable {

  private final List<Row> rows;

  private SheetTable(List<Row> rows) {
    this.rows = rows;
  }

  /**
   * Parses the raw rows of a tab.
   *
   * @param required columns that must be in the header; not checked for an empty tab
   * @throws SheetsException.InvalidLayout when a required column is missing
   */
  static SheetTable parse(String tab, List<List<String>> raw, Collection<String> required) {
    if (raw.isEmpty()) {
      return new SheetTable(List.of());
    }
    Map<String, Integer> columns = new HashMap<>();
    List<String> header = raw.get(0);
    for (int i = 0; i < header.size(); i++) {
      String name = header.get(i) == null ? "" : header.get(i).strip().toLowerCase(Locale.ROOT);
      if (!name.isEmpty()) {
        columns.putIfAbsent(name, i);
      }
    }
    List<String> missing = required.stream().filter(c -> !columns.containsKey(c)).toList();
    if (!missing.isEmpty()) {
      throw new SheetsException.InvalidLayout(tab, missing);
    }
    List<Row> rows = new ArrayList<>();
    for (List<String> cells : raw.subList(1, raw.size())) {
      Row row = new Row(columns, cells);
      if (!row.isBlank()) {
        rows.add(row);
      }
    }
    return new SheetTable(List.copyOf(rows));
  }

  /** The data rows below the header; completely blank rows are left out. */
  List<Row> rows() {
    return rows;
  }

  /** One data row. A missing or empty cell reads as an empty string. */
  record Row(Map<String, Integer> columns, List<String> cells) {

    /** The trimmed text of the cell in the named column. */
    String get(String column) {
      Integer index = columns.get(column);
      if (index == null || index >= cells.size() || cells.get(index) == null) {
        return "";
      }
      return cells.get(index).strip();
    }

    boolean isBlank() {
      return cells.stream().allMatch(cell -> cell == null || cell.isBlank());
    }
  }
}

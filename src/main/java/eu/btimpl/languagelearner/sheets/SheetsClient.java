package eu.btimpl.languagelearner.sheets;

import java.util.List;

/** Reads raw cells from the teacher's spreadsheet. */
public interface SheetsClient {

  /**
   * Returns the rows of a tab, header included. Like the Sheets API, rows may be shorter than the
   * header (empty trailing cells are left out) and an empty tab gives an empty list.
   *
   * @throws SheetsException when the tab cannot be read
   */
  List<List<String>> readTab(String tab);
}

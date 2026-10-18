package eu.btimpl.languagelearner.sheets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the teacher's spreadsheet.
 *
 * @param spreadsheetId id of the spreadsheet (the part of its URL between {@code /d/} and {@code
 *     /edit}); empty until configured
 * @param studentsTab name of the tab that lists the students
 */
@ConfigurationProperties("app.sheets")
public record SheetsProperties(String spreadsheetId, String studentsTab) {

  public static final String DEFAULT_STUDENTS_TAB = "Students";

  public SheetsProperties {
    spreadsheetId = spreadsheetId == null ? "" : spreadsheetId.strip();
    studentsTab =
        studentsTab == null || studentsTab.isBlank() ? DEFAULT_STUDENTS_TAB : studentsTab.strip();
  }
}

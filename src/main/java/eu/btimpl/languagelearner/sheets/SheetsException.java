package eu.btimpl.languagelearner.sheets;

import java.util.List;

/** Anything that goes wrong while reading the spreadsheet, in terms the rest of the app uses. */
public class SheetsException extends RuntimeException {

  protected SheetsException(String message, Throwable cause) {
    super(message, cause);
  }

  /** The tab does not exist in the spreadsheet. */
  public static final class TabNotFound extends SheetsException {

    public TabNotFound(String tab, Throwable cause) {
      super("Tab '" + tab + "' was not found in the spreadsheet", cause);
    }
  }

  /** The credentials are missing or the service account may not read the spreadsheet. */
  public static final class AccessDenied extends SheetsException {

    public AccessDenied(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** The Sheets API rejected the request because a quota is used up. */
  public static final class QuotaExceeded extends SheetsException {

    public QuotaExceeded(Throwable cause) {
      super("The Google Sheets API quota is exceeded", cause);
    }
  }

  /** The Sheets API could not be reached or failed in some other way. */
  public static final class Unavailable extends SheetsException {

    public Unavailable(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** The tab does not have the columns the app needs. */
  public static final class InvalidLayout extends SheetsException {

    public InvalidLayout(String tab, List<String> missingColumns) {
      super(
          "Tab '" + tab + "' is missing the column(s): " + String.join(", ", missingColumns), null);
    }
  }
}

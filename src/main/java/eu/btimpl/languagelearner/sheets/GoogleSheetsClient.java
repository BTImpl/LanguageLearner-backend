package eu.btimpl.languagelearner.sheets;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link SheetsClient} on the official Google Sheets API client.
 *
 * <p>Authentication is the application default one: on Cloud Run the service account the service
 * runs as, locally the account in {@code GOOGLE_APPLICATION_CREDENTIALS} or the one set up with
 * {@code gcloud auth application-default login}. Only read access is asked for. The client is built
 * on the first read, so the application starts without credentials.
 */
@Component
class GoogleSheetsClient implements SheetsClient {

  private static final String APPLICATION_NAME = "languagelearner-backend";
  private static final int TIMEOUT_MILLIS = 10_000;

  private final SheetsProperties properties;
  private volatile Sheets sheets;

  GoogleSheetsClient(SheetsProperties properties) {
    this.properties = properties;
  }

  @Override
  public List<List<String>> readTab(String tab) {
    String spreadsheetId = properties.spreadsheetId();
    if (spreadsheetId.isEmpty()) {
      throw new IllegalStateException(
          "The spreadsheet is not configured: set SHEETS_SPREADSHEET_ID (app.sheets.spreadsheet-id)");
    }
    Sheets service = service();
    try {
      ValueRange response =
          service.spreadsheets().values().get(spreadsheetId, range(tab)).execute();
      return toRows(response.getValues());
    } catch (GoogleJsonResponseException e) {
      throw translate(tab, e);
    } catch (IOException e) {
      throw new SheetsException.Unavailable("Could not reach the Google Sheets API", e);
    }
  }

  /** The A1 range of a whole tab: its name in single quotes, which are doubled inside the name. */
  static String range(String tab) {
    return "'" + tab.replace("'", "''") + "'";
  }

  /** Maps a failed Sheets API call to the exception the rest of the app knows. */
  static SheetsException translate(String tab, GoogleJsonResponseException e) {
    String detail =
        e.getDetails() != null && e.getDetails().getMessage() != null
            ? e.getDetails().getMessage()
            : String.valueOf(e.getMessage());
    return switch (e.getStatusCode()) {
      // The API answers 400 "Unable to parse range" for a tab that does not exist.
      case 400 ->
          detail.contains("Unable to parse range")
              ? new SheetsException.TabNotFound(tab, e)
              : new SheetsException.Unavailable(
                  "The Sheets API rejected the request: " + detail, e);
      case 403, 404 ->
          new SheetsException.AccessDenied(
              "The spreadsheet was not found or may not be read by this account: " + detail, e);
      case 429 -> new SheetsException.QuotaExceeded(e);
      default -> new SheetsException.Unavailable("The Sheets API failed: " + detail, e);
    };
  }

  private static List<List<String>> toRows(List<List<Object>> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(row -> row.stream().map(cell -> cell == null ? "" : cell.toString()).toList())
        .toList();
  }

  private Sheets service() {
    Sheets current = sheets;
    if (current == null) {
      synchronized (this) {
        if (sheets == null) {
          sheets = createService();
        }
        current = sheets;
      }
    }
    return current;
  }

  private static Sheets createService() {
    try {
      GoogleCredentials credentials =
          GoogleCredentials.getApplicationDefault()
              .createScoped(SheetsScopes.SPREADSHEETS_READONLY);
      HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
      HttpRequestInitializer initializer =
          request -> {
            adapter.initialize(request);
            request.setConnectTimeout(TIMEOUT_MILLIS);
            request.setReadTimeout(TIMEOUT_MILLIS);
          };
      return new Sheets.Builder(
              GoogleNetHttpTransport.newTrustedTransport(),
              GsonFactory.getDefaultInstance(),
              initializer)
          .setApplicationName(APPLICATION_NAME)
          .build();
    } catch (GeneralSecurityException e) {
      throw new SheetsException.Unavailable("Could not set up the HTTP transport", e);
    } catch (IOException e) {
      throw new SheetsException.AccessDenied("Could not load the Google credentials", e);
    }
  }
}

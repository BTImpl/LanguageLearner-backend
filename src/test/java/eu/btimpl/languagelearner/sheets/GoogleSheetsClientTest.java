package eu.btimpl.languagelearner.sheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;

class GoogleSheetsClientTest {

  private static GoogleJsonResponseException failure(int status, String message) {
    GoogleJsonError details = new GoogleJsonError();
    details.setCode(status);
    details.setMessage(message);
    return new GoogleJsonResponseException(
        new HttpResponseException.Builder(status, "status " + status, new HttpHeaders()), details);
  }

  @Test
  void aWholeTabIsAskedForByItsQuotedName() {
    assertThat(GoogleSheetsClient.range("Tomi")).isEqualTo("'Tomi'");
    assertThat(GoogleSheetsClient.range("Tom's tab")).isEqualTo("'Tom''s tab'");
  }

  @Test
  void anUnparsableRangeMeansTheTabIsMissing() {
    SheetsException result =
        GoogleSheetsClient.translate("Tomi", failure(400, "Unable to parse range: Tomi"));

    assertThat(result).isInstanceOf(SheetsException.TabNotFound.class);
    assertThat(result).hasMessageContaining("Tomi");
  }

  @Test
  void otherBadRequestsAreUnavailable() {
    assertThat(GoogleSheetsClient.translate("Tomi", failure(400, "Something else")))
        .isInstanceOf(SheetsException.Unavailable.class);
  }

  @Test
  void forbiddenAndNotFoundMeanNoAccess() {
    assertThat(
            GoogleSheetsClient.translate(
                "Tomi", failure(403, "The caller does not have permission")))
        .isInstanceOf(SheetsException.AccessDenied.class);
    assertThat(
            GoogleSheetsClient.translate("Tomi", failure(404, "Requested entity was not found.")))
        .isInstanceOf(SheetsException.AccessDenied.class);
  }

  @Test
  void tooManyRequestsMeansQuotaExceeded() {
    assertThat(GoogleSheetsClient.translate("Tomi", failure(429, "Quota exceeded")))
        .isInstanceOf(SheetsException.QuotaExceeded.class);
  }

  @Test
  void serverErrorsAreUnavailable() {
    assertThat(GoogleSheetsClient.translate("Tomi", failure(500, "Internal error")))
        .isInstanceOf(SheetsException.Unavailable.class);
    assertThat(GoogleSheetsClient.translate("Tomi", failure(503, "Try later")))
        .isInstanceOf(SheetsException.Unavailable.class);
  }

  @Test
  void readingWithoutAConfiguredSpreadsheetIsAConfigurationError() {
    GoogleSheetsClient client = new GoogleSheetsClient(new SheetsProperties("", "Students"));

    assertThatThrownBy(() -> client.readTab("Students"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SHEETS_SPREADSHEET_ID");
  }
}

package eu.btimpl.languagelearner.auth;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Google sign-in check.
 *
 * @param googleClientId the OAuth client ids the app signs in with, separated by {@code ;}, a comma
 *     or whitespace: the web client and the iOS client (a native iOS sign-in issues the token for
 *     the iOS client, not for the web one). A token is only accepted when it was issued for one of
 *     them (the {@code aud} claim). While empty, every token is rejected.
 * @param googleJwkSetUri where Google publishes the keys its tokens are signed with
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String googleClientId, String googleJwkSetUri) {

  public static final String DEFAULT_GOOGLE_JWK_SET_URI =
      "https://www.googleapis.com/oauth2/v3/certs";

  private static final Pattern SEPARATORS = Pattern.compile("[;,\\s]+");

  public AuthProperties {
    googleClientId = googleClientId == null ? "" : googleClientId.strip();
    googleJwkSetUri =
        googleJwkSetUri == null || googleJwkSetUri.isBlank()
            ? DEFAULT_GOOGLE_JWK_SET_URI
            : googleJwkSetUri.strip();
  }

  /** The accepted client ids, without blanks and duplicates. */
  public List<String> googleClientIds() {
    return SEPARATORS.splitAsStream(googleClientId).filter(id -> !id.isBlank()).distinct().toList();
  }
}

package eu.btimpl.languagelearner.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Google sign-in check.
 *
 * @param googleClientId the web OAuth client id; a token is only accepted when it was issued for it
 *     (the {@code aud} claim). While empty, every token is rejected.
 * @param googleJwkSetUri where Google publishes the keys its tokens are signed with
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String googleClientId, String googleJwkSetUri) {

  public static final String DEFAULT_GOOGLE_JWK_SET_URI =
      "https://www.googleapis.com/oauth2/v3/certs";

  public AuthProperties {
    googleClientId = googleClientId == null ? "" : googleClientId.strip();
    googleJwkSetUri =
        googleJwkSetUri == null || googleJwkSetUri.isBlank()
            ? DEFAULT_GOOGLE_JWK_SET_URI
            : googleJwkSetUri.strip();
  }
}

package eu.btimpl.languagelearner.auth;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * The checks a Google ID token must pass besides its signature: not expired, issued by Google,
 * issued for this app (one of its OAuth clients), and with a verified e-mail address.
 */
final class GoogleTokenValidators {

  /** Google writes its issuer both with and without the scheme. */
  private static final List<String> ISSUERS =
      List.of("https://accounts.google.com", "accounts.google.com");

  private GoogleTokenValidators() {}

  /**
   * @param clientIds the OAuth client ids a token may be issued for; blanks are ignored. With none,
   *     every token is rejected.
   */
  static OAuth2TokenValidator<Jwt> create(Collection<String> clientIds) {
    Set<String> allowed =
        clientIds.stream().filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
    List<OAuth2TokenValidator<Jwt>> validators =
        List.of(
            new JwtTimestampValidator(),
            GoogleTokenValidators::issuer,
            token -> audience(token, allowed),
            GoogleTokenValidators::verifiedEmail);
    return new DelegatingOAuth2TokenValidator<>(validators);
  }

  private static OAuth2TokenValidatorResult issuer(Jwt token) {
    Object issuer = token.getClaim("iss");
    if (issuer != null && ISSUERS.contains(issuer.toString())) {
      return OAuth2TokenValidatorResult.success();
    }
    return failure("The token was not issued by Google");
  }

  private static OAuth2TokenValidatorResult audience(Jwt token, Set<String> clientIds) {
    List<String> audience = token.getAudience();
    if (audience != null && audience.stream().anyMatch(clientIds::contains)) {
      return OAuth2TokenValidatorResult.success();
    }
    return failure("The token was not issued for this app");
  }

  private static OAuth2TokenValidatorResult verifiedEmail(Jwt token) {
    String email = token.getClaimAsString("email");
    Object verified = token.getClaim("email_verified");
    boolean isVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
    if (email != null && !email.isBlank() && isVerified) {
      return OAuth2TokenValidatorResult.success();
    }
    return failure("The e-mail address of the token is missing or not verified");
  }

  private static OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}

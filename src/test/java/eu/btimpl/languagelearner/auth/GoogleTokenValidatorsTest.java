package eu.btimpl.languagelearner.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleTokenValidatorsTest {

  private static final String CLIENT_ID = "web-client-id";
  private static final String IOS_CLIENT_ID = "ios-client-id";

  private final OAuth2TokenValidator<Jwt> validator =
      GoogleTokenValidators.create(java.util.List.of(CLIENT_ID, IOS_CLIENT_ID));

  private static Jwt token(Consumer<Jwt.Builder> customizer) {
    Instant now = Instant.now();
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", "https://accounts.google.com")
            .claim("aud", java.util.List.of(CLIENT_ID))
            .claim("sub", "1234")
            .claim("email", "tomi@example.com")
            .claim("email_verified", true)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)));
    customizer.accept(builder);
    return builder.build();
  }

  private boolean accepts(Consumer<Jwt.Builder> customizer) {
    return !validator.validate(token(customizer)).hasErrors();
  }

  @Test
  void acceptsAValidToken() {
    assertThat(accepts(builder -> {})).isTrue();
  }

  @Test
  void acceptsBothSpellingsOfTheGoogleIssuer() {
    assertThat(accepts(builder -> builder.claim("iss", "accounts.google.com"))).isTrue();
    assertThat(accepts(builder -> builder.claim("iss", "https://accounts.google.com"))).isTrue();
  }

  @Test
  void rejectsAnotherIssuer() {
    assertThat(accepts(builder -> builder.claim("iss", "https://evil.example.com"))).isFalse();
  }

  @Test
  void rejectsATokenWithoutIssuer() {
    assertThat(accepts(builder -> builder.claims(claims -> claims.remove("iss")))).isFalse();
  }

  @Test
  void rejectsATokenForAnotherApp() {
    assertThat(accepts(builder -> builder.claim("aud", java.util.List.of("someone-else"))))
        .isFalse();
  }

  @Test
  void acceptsATokenWhoseAudienceListsOurClientAmongOthers() {
    assertThat(accepts(builder -> builder.claim("aud", java.util.List.of("other", CLIENT_ID))))
        .isTrue();
  }

  @Test
  void acceptsATokenForAnyOfTheConfiguredClients() {
    assertThat(accepts(builder -> builder.claim("aud", java.util.List.of(CLIENT_ID)))).isTrue();
    assertThat(accepts(builder -> builder.claim("aud", java.util.List.of(IOS_CLIENT_ID)))).isTrue();
  }

  @Test
  void rejectsATokenForAClientThatIsNotConfigured() {
    OAuth2TokenValidator<Jwt> webOnly = GoogleTokenValidators.create(java.util.List.of(CLIENT_ID));

    assertThat(
            webOnly
                .validate(token(builder -> builder.claim("aud", java.util.List.of(IOS_CLIENT_ID))))
                .hasErrors())
        .isTrue();
  }

  @Test
  void rejectsATokenWithoutAudience() {
    assertThat(accepts(builder -> builder.claims(claims -> claims.remove("aud")))).isFalse();
  }

  @Test
  void rejectsEveryTokenWhileNoClientIdIsConfigured() {
    OAuth2TokenValidator<Jwt> unconfigured =
        GoogleTokenValidators.create(java.util.List.of("", " "));

    assertThat(
            unconfigured
                .validate(token(builder -> builder.claim("aud", java.util.List.of(""))))
                .hasErrors())
        .isTrue();
    assertThat(unconfigured.validate(token(builder -> {})).hasErrors()).isTrue();
  }

  @Test
  void rejectsAnExpiredToken() {
    Instant now = Instant.now();

    assertThat(
            accepts(
                builder ->
                    builder
                        .issuedAt(now.minus(Duration.ofHours(2)))
                        .expiresAt(now.minus(Duration.ofHours(1)))))
        .isFalse();
  }

  @Test
  void rejectsATokenWhoseEmailIsNotVerified() {
    assertThat(accepts(builder -> builder.claim("email_verified", false))).isFalse();
    assertThat(accepts(builder -> builder.claims(claims -> claims.remove("email_verified"))))
        .isFalse();
  }

  @Test
  void acceptsAVerifiedFlagWrittenAsText() {
    assertThat(accepts(builder -> builder.claim("email_verified", "true"))).isTrue();
    assertThat(accepts(builder -> builder.claim("email_verified", "false"))).isFalse();
  }

  @Test
  void rejectsATokenWithoutEmail() {
    assertThat(accepts(builder -> builder.claims(claims -> claims.remove("email")))).isFalse();
    assertThat(accepts(builder -> builder.claim("email", " "))).isFalse();
  }
}

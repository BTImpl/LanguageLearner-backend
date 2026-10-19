package eu.btimpl.languagelearner.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

  private static AuthProperties withClientId(String value) {
    return new AuthProperties(value, null);
  }

  @Test
  void aSingleClientIdIsOneClient() {
    assertThat(withClientId("web.apps.googleusercontent.com").googleClientIds())
        .containsExactly("web.apps.googleusercontent.com");
  }

  @Test
  void clientIdsCanBeSeparatedBySemicolonCommaOrWhitespace() {
    assertThat(withClientId("web;ios").googleClientIds()).containsExactly("web", "ios");
    assertThat(withClientId("web,ios").googleClientIds()).containsExactly("web", "ios");
    assertThat(withClientId("web ios").googleClientIds()).containsExactly("web", "ios");
    assertThat(withClientId(" web ; ios,\nandroid ").googleClientIds())
        .containsExactly("web", "ios", "android");
  }

  @Test
  void blanksAndDuplicatesAreDropped() {
    assertThat(withClientId("web;;ios;web;").googleClientIds()).containsExactly("web", "ios");
  }

  @Test
  void noClientIdMeansNoClients() {
    assertThat(withClientId(null).googleClientIds()).isEmpty();
    assertThat(withClientId("").googleClientIds()).isEmpty();
    assertThat(withClientId("  ; ").googleClientIds()).isEmpty();
  }
}

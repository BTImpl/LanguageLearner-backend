package eu.btimpl.languagelearner.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Sign-in tokens for tests over real HTTP. They are signed with a key generated here; {@link
 * Config}, imported with {@code @Import}, makes the app trust that key instead of Google's. Every
 * other check on the token is the production one.
 */
public final class AuthTestSupport {

  public static final String CLIENT_ID = "test-web-client-id";

  private static final KeyPair KEYS = generateKeys();

  private AuthTestSupport() {}

  /** Makes the app accept tokens signed with this class's key. */
  @TestConfiguration
  public static class Config {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
      decoder.setJwtValidator(GoogleTokenValidators.create(java.util.List.of(CLIENT_ID)));
      return decoder;
    }
  }

  public static KeyPair generateKeys() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A valid token of the given e-mail address. */
  public static String tokenOf(String email) throws Exception {
    return token(claims -> claims.claim("email", email));
  }

  /** A valid token of {@code tomi@example.com}, changed by the customizer. */
  public static String token(Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
    return token(KEYS, customizer);
  }

  /** Like {@link #token(Consumer)}, but signed with the given key. */
  public static String token(KeyPair signingKeys, Consumer<JWTClaimsSet.Builder> customizer)
      throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .audience(CLIENT_ID)
            .subject("1234567890")
            .claim("email", "tomi@example.com")
            .claim("email_verified", true)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
    customizer.accept(claims);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
    jwt.sign(new RSASSASigner(signingKeys.getPrivate()));
    return jwt.serialize();
  }
}

package eu.btimpl.languagelearner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.btimpl.languagelearner.sheets.SheetsClient;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The whole sign-in check over real HTTP. The tokens are signed with a key generated here, which
 * replaces Google's keys; every other check is the production one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {

  private static final String CLIENT_ID = "test-web-client-id";
  private static final KeyPair KEYS = generateKeys();

  private static final List<List<String>> STUDENTS =
      List.of(
          List.of("email", "name", "tab"),
          List.of("tomi@example.com", "Bodis Tamas", "Tomi"),
          List.of("anna@example.com", "Kiss Anna", "Anna"));

  @TestConfiguration
  static class TestKeys {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
      decoder.setJwtValidator(GoogleTokenValidators.create(CLIENT_ID));
      return decoder;
    }
  }

  @Value("${local.server.port}")
  private int port;

  @MockitoBean private SheetsClient sheetsClient;

  @BeforeEach
  void theSheetListsTheStudents() {
    when(sheetsClient.readTab("Students")).thenReturn(STUDENTS);
  }

  private static KeyPair generateKeys() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String token(KeyPair signingKeys, Consumer<JWTClaimsSet.Builder> customizer)
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

  private static String token(Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
    return token(KEYS, customizer);
  }

  private HttpResponse<String> send(String method, String path, String bearerToken)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (bearerToken != null) {
      request.header("Authorization", "Bearer " + bearerToken);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String bearerToken) throws Exception {
    return send("GET", path, bearerToken);
  }

  private void assertUnauthorized(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
  }

  @Test
  void aValidTokenOfAStudentOnTheListGetsTheirOwnData() throws Exception {
    HttpResponse<String> response = get("/api/v1/me", token(claims -> {}));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"email\":\"tomi@example.com\"");
    assertThat(response.body()).contains("\"name\":\"Bodis Tamas\"");
  }

  @Test
  void theTokenDecidesWhoTheStudentIsNotAnythingInTheRequest() throws Exception {
    String annasToken = token(claims -> claims.claim("email", "anna@example.com"));

    HttpResponse<String> response = get("/api/v1/me?email=tomi@example.com", annasToken);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"email\":\"anna@example.com\"");
    assertThat(response.body()).doesNotContain("tomi@example.com");
  }

  @Test
  void theEmailOfTheTokenIsMatchedIgnoringCase() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/me", token(claims -> claims.claim("email", "Tomi@Example.COM")));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"name\":\"Bodis Tamas\"");
  }

  @Test
  void aMissingTokenIsUnauthorized() throws Exception {
    assertUnauthorized(get("/api/v1/me", null));
  }

  @Test
  void aGarbageTokenIsUnauthorized() throws Exception {
    assertUnauthorized(get("/api/v1/me", "not-a-token"));
  }

  @Test
  void anExpiredTokenIsUnauthorized() throws Exception {
    Instant now = Instant.now();
    String expired =
        token(
            claims ->
                claims
                    .issueTime(Date.from(now.minus(Duration.ofHours(2))))
                    .expirationTime(Date.from(now.minus(Duration.ofHours(1)))));

    assertUnauthorized(get("/api/v1/me", expired));
  }

  @Test
  void aTokenForAnotherAppIsUnauthorized() throws Exception {
    assertUnauthorized(get("/api/v1/me", token(claims -> claims.audience("another-app"))));
  }

  @Test
  void aTokenFromAnotherIssuerIsUnauthorized() throws Exception {
    assertUnauthorized(
        get("/api/v1/me", token(claims -> claims.issuer("https://evil.example.com"))));
  }

  @Test
  void aTokenWithAnUnverifiedEmailIsUnauthorized() throws Exception {
    assertUnauthorized(get("/api/v1/me", token(claims -> claims.claim("email_verified", false))));
  }

  @Test
  void aTokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
    String forged = token(generateKeys(), claims -> {});

    assertUnauthorized(get("/api/v1/me", forged));
  }

  @Test
  void aStudentNotOnTheListIsForbidden() throws Exception {
    HttpResponse<String> response =
        get("/api/v1/me", token(claims -> claims.claim("email", "stranger@example.com")));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"code\":\"STUDENT_NOT_FOUND\"");
  }

  @Test
  void anUnreadableSheetIsServiceUnavailable() throws Exception {
    when(sheetsClient.readTab("Students"))
        .thenThrow(new SheetsException.Unavailable("Sheets is down", null));

    HttpResponse<String> response = get("/api/v1/me", token(claims -> {}));

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"code\":\"SHEET_UNAVAILABLE\"");
  }

  @Test
  void unknownPathsAndWrongMethodsUseTheUniformErrorFormat() throws Exception {
    String token = token(claims -> {});

    HttpResponse<String> notFound = get("/api/v1/nothing-here", token);
    HttpResponse<String> wrongMethod = send("POST", "/api/v1/me", token);

    assertThat(notFound.statusCode()).isEqualTo(404);
    assertThat(notFound.body()).contains("\"code\":\"NOT_FOUND\"");
    assertThat(wrongMethod.statusCode()).isEqualTo(405);
    assertThat(wrongMethod.body()).contains("\"code\":\"METHOD_NOT_ALLOWED\"");
  }

  @Test
  void theHealthEndpointStaysOpenAndDoesNotTouchTheSheet() throws Exception {
    HttpResponse<String> response = get("/actuator/health", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}

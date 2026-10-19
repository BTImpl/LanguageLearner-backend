package eu.btimpl.languagelearner.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Verifies Google ID tokens: the signature against Google's public keys, then the claims. */
@Configuration(proxyBeanMethods = false)
class GoogleJwtDecoderConfig {

  private static final Logger log = LoggerFactory.getLogger(GoogleJwtDecoderConfig.class);

  @Bean
  JwtDecoder googleJwtDecoder(AuthProperties properties) {
    if (properties.googleClientIds().isEmpty()) {
      log.warn("AUTH_GOOGLE_CLIENT_ID is not set: every sign-in token will be rejected");
    }
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.googleJwkSetUri())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(GoogleTokenValidators.create(properties.googleClientIds()));
    return decoder;
  }
}

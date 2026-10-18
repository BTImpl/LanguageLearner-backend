package eu.btimpl.languagelearner.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI description of the API, served at {@code /v3/api-docs}. Like every endpoint except
 * the health check it needs a valid sign-in token.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  OpenAPI languageLearnerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("LanguageLearner API")
                .version("v1")
                .description("Decks of the signed-in student and, later, their practice results."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The Google ID token of the student")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}

package eu.btimpl.languagelearner.config;

import eu.btimpl.languagelearner.api.ApiErrors;
import eu.btimpl.languagelearner.attempts.StudentRegistry;
import eu.btimpl.languagelearner.auth.StudentLookupFilter;
import eu.btimpl.languagelearner.sheets.StudentDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Web security. Every endpoint needs a valid Google ID token ({@code Authorization: Bearer ...}) of
 * a student on the teacher's list; only the health check is open. There is no session and no CSRF
 * protection: the API is stateless and token based. Errors use the uniform {@code ApiError} body.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  private static final AuthenticationEntryPoint UNAUTHORIZED =
      (request, response, exception) -> {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ApiErrors.write(
            response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid sign-in token is required");
      };

  private static final AccessDeniedHandler FORBIDDEN =
      (request, response, exception) ->
          ApiErrors.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied");

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, StudentDirectory students, StudentRegistry registry) throws Exception {
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(UNAUTHORIZED)
                    .accessDeniedHandler(FORBIDDEN))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN))
        .addFilterAfter(
            new StudentLookupFilter(students, registry), BearerTokenAuthenticationFilter.class);
    return http.build();
  }
}

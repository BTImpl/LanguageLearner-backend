package eu.btimpl.languagelearner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security. For now every endpoint is open; the sign-in check (Google ID token) closes them in
 * BE-04. There is no session and no CSRF protection: the API is stateless and token based.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
  }
}

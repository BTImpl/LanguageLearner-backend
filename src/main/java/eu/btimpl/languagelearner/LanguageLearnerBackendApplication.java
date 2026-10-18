package eu.btimpl.languagelearner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the LanguageLearner backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LanguageLearnerBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(LanguageLearnerBackendApplication.class, args);
  }
}

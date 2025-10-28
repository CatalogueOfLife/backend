package life.catalogue.matching.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${cors.allowed-origins:*}")
  private String allowedOrigins;

  @Value("${cors.allowed-methods: GET,POST,HEAD,OPTIONS}")
  private String[] allowedMethods;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**") // or specific path
      .allowedOrigins(allowedOrigins) // or "*"
      .allowedMethods(allowedMethods)
      .allowedHeaders("*");
  }
}
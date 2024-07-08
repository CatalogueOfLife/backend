package life.catalogue.matching;

import io.swagger.v3.oas.models.ExternalDocumentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import life.catalogue.matching.model.APIMetadata;
import life.catalogue.matching.service.MatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.text.NumberFormat;
import java.util.Optional;

/**
 * Main class to run the web services.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("web")
public class MatchingApplication implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MatchingApplication.class);

  protected final MatchingService matchingService;

  @Value("${version}") String version;
  @Value("${licence}") String licence;
  @Value("${licence.url}") String licenceUrl;

  public MatchingApplication(MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @Override
  public void run(ApplicationArguments args) {

    Optional<APIMetadata> metadata = matchingService.getAPIMetadata();
    if (metadata.isEmpty()) {
      LOG.error("No main index found. Cannot start web services");
      return;
    }

    metadata.ifPresent(m -> {
      LOG.info("Web services started. Index size: {} taxa, size on disk: {}",
      NumberFormat.getInstance().format(
        m.getMainIndex().getNameUsageCount()),
        m.getMainIndex().getSizeInMB() > 0 ? NumberFormat.getInstance().format(m.getMainIndex().getSizeInMB()) + "MB" : "unknown");
    });
  }

  @Bean
  public OpenAPI customOpenAPI() {
    Optional<APIMetadata> metadata = matchingService.getAPIMetadata();
    OpenAPI openAPI = new OpenAPI();
    metadata.ifPresent(m -> {
      String title = m.getMainIndex().getDatasetTitle() != null ?
        m.getMainIndex().getDatasetTitle() + " Matching Service API" :
        "COL Matching Service API";
      String description = "API for matching scientific names to taxa in the checklist" +
        (m.getMainIndex().getDatasetTitle() != null ? " " + m.getMainIndex().getDatasetTitle() : "");

          openAPI.info(new Info()
          .title(title)
          .description(description)
          .version(version)
          .license(new License()
            .name(licence)
            .url(licenceUrl)));

      if (m.getMainIndex().getDatasetKey() != null) {
        openAPI.externalDocs(new ExternalDocumentation()
          .description(m.getMainIndex().getDatasetTitle())
          .url("https://checklistbank.org/dataset/" + m.getMainIndex().getDatasetKey()));
      }
    });

    return openAPI;
  }

}

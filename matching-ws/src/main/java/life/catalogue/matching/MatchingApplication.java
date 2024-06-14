package life.catalogue.matching;

import io.swagger.v3.oas.models.ExternalDocumentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

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

/**
 * Main application class for the matching-ws module.
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
    APIMetadata metadata = matchingService.getIndexMetadata();
    LOG.info("Web services started. Index size: {} taxa, size on disk: {}",
      NumberFormat.getInstance().format(metadata.getMainIndex().getNameUsageCount()),
      metadata.getMainIndex().getSizeInMB() > 0 ? NumberFormat.getInstance().format(metadata.getMainIndex().getSizeInMB()) + "MB" : "unknown"
    );
  }

  @Bean
  public OpenAPI customOpenAPI() {
    APIMetadata metadata = matchingService.getIndexMetadata();
    String title = metadata.getMainIndex().getDatasetTitle() != null ?
      metadata.getMainIndex().getDatasetTitle() + " Matching Service API" :
      "COL Matching Service API";
    String description = "API for matching scientific names to taxa in the checklist" +
      (metadata.getMainIndex().getDatasetTitle() != null ? " " + metadata.getMainIndex().getDatasetTitle() : "");

    OpenAPI openAPI = new OpenAPI()
      .info(new Info()
        .title(title)
        .description(description)
        .version(version)
        .license(new License()
          .name(licence)
          .url(licenceUrl)));

    if (metadata.getMainIndex().getDatasetKey() != null) {
      openAPI.externalDocs(new ExternalDocumentation()
        .description(metadata.getMainIndex().getDatasetTitle())
        .url("https://checklistbank.org/dataset/" + metadata.getMainIndex().getDatasetKey()));
    }

    return openAPI;
  }

}

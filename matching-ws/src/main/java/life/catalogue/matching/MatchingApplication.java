package life.catalogue.matching;

import io.swagger.v3.oas.models.ExternalDocumentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.model.APIMetadata;
import life.catalogue.matching.service.IndexingService;
import life.catalogue.matching.service.MatchingService;
import life.catalogue.matching.util.NameParsers;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static life.catalogue.matching.Main.*;

/**
 * Main class to run the web services.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("web")
@Slf4j
public class MatchingApplication implements ApplicationRunner {

  protected final ApplicationContext appContext;
  protected final IndexingService indexingService;
  protected final MatchingService matchingService;
  protected final DatasetIndex datasetIndex;

  @Value("${version}") String version;
  @Value("${licence.name}") String licence;
  @Value("${licence.url}") String licenceUrl;
  @Value("${mode:INDEX_AND_RUN}") String configuredMode;
  @Value("${clb.dataset.id: }") String configuredDatasetId;
  @Value("${clb.identifier.dataset.ids: }") List<String> configuredIdentifierDatasetIds;
  @Value("${clb.iucn.dataset.id: }") String configuredIucnDatasetId;
  @Value("${index.path:/tmp/matching-index}") String indexPath;
  @Value("${export.path:/tmp/matching-export}") String exportPath;

  public MatchingApplication(MatchingService matchingService,
                             IndexingService indexingService,
                             DatasetIndex datasetIndex, ApplicationContext appContext) {
    this.matchingService = matchingService;
    this.indexingService = indexingService;
    this.datasetIndex = datasetIndex;
    this.appContext = appContext;
  }

  @Override
  public void run(ApplicationArguments args) {

    ExecutionMode mode = getMode(args);

    // if index does not exist, run indexing
    if (Main.ExecutionMode.INDEX.equals(mode) || Main.ExecutionMode.INDEX_AND_RUN.equals(mode)) {
      if (!datasetIndex.exists(indexPath)) {
        try {
          runIndexingIfRequired(args);
        } catch (Exception e) {
          log.error("Failed to run indexing", e);
        }
      } else {
        log.info("Index available at {}", indexPath);
      }
    }

    // if mode is INDEX  exit after indexing
    if (Main.ExecutionMode.INDEX.equals(mode)) {
      SpringApplication.exit(appContext);
    } else {
      initialiseWebapp();
    }
  }

  private void initialiseWebapp() {
    Optional<APIMetadata> metadata = matchingService.getAPIMetadata();
    if (metadata.isEmpty()) {
      log.error("No main index found. Cannot start web services");
      return;
    }

    try {
      log.info("Loading name parser configs from ChecklistBank");
      NameParsers.INSTANCE.configs().loadFromCLB();
    } catch (IOException e) {
      log.error("Failed to load name parser configs from CLB", e);
    } catch (InterruptedException e) {
      log.warn("Interrupted. Failed to load name parser configs from CLB.", e);
    }

    metadata.ifPresent(m -> {
      if (m.getBuildInfo() != null) {
        log.info("Git commit ID: {}", m.getBuildInfo().getSha());
      }
      log.info("Web services started. Index size: {} taxa, size on disk: {}",
      NumberFormat.getInstance().format(
        m.getMainIndex().getNameUsageCount()),
        m.getMainIndex().getSizeInMB() > 0 ? NumberFormat.getInstance().format(m.getMainIndex().getSizeInMB()) + "MB" : "unknown");
    });
  }

  private void runIndexingIfRequired(ApplicationArguments args) throws Exception {

    // get dataset IDs, allowing for command line overrides
    final String datasetId = getMainDatasetId(args);
    final String iucnDatasetId = getIucnDatasetId(args);
    final List<String> identifierDatasetIds = getIdentifierDatasetIds(args);

    // build the main index
    indexingService.createMainIndex(datasetId);

    // reinitialise dataset index for the new index
    datasetIndex.reinit();

    // build iucn index
    if (Objects.nonNull(iucnDatasetId)) {
      indexingService.indexIUCN(iucnDatasetId);
    }

    // build identifier indexes
    for (String id : identifierDatasetIds) {
        indexingService.indexIdentifiers(id);
    }
    log.info("Indexing completed");
  }

  private ExecutionMode getMode(ApplicationArguments args) {
    ExecutionMode mode = configuredMode != null ? ExecutionMode.valueOf(configuredMode) : ExecutionMode.INDEX_AND_RUN;
    if (args.getOptionValues(Main.MODE) != null && !args.getOptionValues(Main.MODE).isEmpty()) {
      mode = ExecutionMode.valueOf(args.getOptionValues(Main.MODE).get(0));
    }
    return mode;
  }

  private String getMainDatasetId(ApplicationArguments args) {
    String datasetId = configuredDatasetId;
    if (args.getOptionValues(Main.CLB_DATASET_ID) != null && !args.getOptionValues(Main.CLB_DATASET_ID).isEmpty()) {
      datasetId = args.getOptionValues(Main.CLB_DATASET_ID).get(0);
    }
    return datasetId;
  }

  private String getIucnDatasetId(ApplicationArguments args) {
    String datasetId = configuredIucnDatasetId;
    if (args.getOptionValues(CLB_IUCN_DATASET_ID) != null && !args.getOptionValues(Main.CLB_IUCN_DATASET_ID).isEmpty()) {
      datasetId = args.getOptionValues(Main.CLB_IUCN_DATASET_ID).get(0);
    }
    return datasetId;
  }

  private List<String> getIdentifierDatasetIds(ApplicationArguments args) {
    List<String> identifierDatasetIds = configuredIdentifierDatasetIds;
    if (args.getOptionValues(CLB_IDENTIFIER_DATASET_IDS) != null && !args.getOptionValues(Main.CLB_IDENTIFIER_DATASET_IDS).isEmpty()) {
      identifierDatasetIds = args.getOptionValues(Main.CLB_IDENTIFIER_DATASET_IDS);
    }
    return identifierDatasetIds;
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

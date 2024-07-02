package life.catalogue.matching;

import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.service.IndexingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Profile;
import java.util.List;
import static life.catalogue.matching.Main.*;

/**
 * Main class to run indexing tasks.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("indexing")
@Slf4j
public class IndexingApplication implements ApplicationRunner {

  final IndexingService indexingService;
  final DatasetIndex datasetIndex;

  public IndexingApplication(IndexingService indexingService, DatasetIndex datasetIndex) {
    this.indexingService = indexingService;
    this.datasetIndex = datasetIndex;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {

    List<String> modes = args.getOptionValues(MODE);
    if (modes == null || modes.isEmpty()) {
      System.err.println("Missing required parameter --" + MODE);
      return;
    }

    String mode = args.getOptionValues(MODE).get(0);
    List<String> datasetIds = args.getOptionValues(Main.CLB_DATASET_ID);

    if (Main.ExecutionMode.BUILD_INDEX.name().equals(mode)) {

      List<String> indexPath = args.getOptionValues(Main.INDEX_PATH);
      List<String> exportPath = args.getOptionValues(Main.EXPORT_PATH);

      // build main index
      final String datasetId = datasetIds.get(0);
      indexingService.writeCLBToFile(datasetId);
      indexingService.createMainIndexFromFile(exportPath.get(0) + "/" + datasetId, indexPath.get(0) );
      datasetIndex.reinit();

      // build iucn index
      List<String> iucnDatasetId = args.getOptionValues(CLB_IUCN_DATASET_ID);
      if (iucnDatasetId != null && !iucnDatasetId.isEmpty()) {
        indexingService.indexIUCN(iucnDatasetId.get(0));
      }

      // build identifier index
      List<String> identifierDatasetIds = args.getOptionValues(CLB_IDENTIFIER_DATASET_IDS);
      if (identifierDatasetIds != null && !identifierDatasetIds.isEmpty()) {
        for (String id : identifierDatasetIds) {
          String[] ids = id.split(",");
          for (String i : ids) {
            indexingService.indexIdentifiers(i);
          }
        }
      }
      log.info("Indexing completed");
    } else if (Main.ExecutionMode.EXPORT_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + CLB_DATASET_ID);
        return;
      }
      indexingService.writeCLBToFile(datasetIds.get(0));
    } else if (Main.ExecutionMode.INDEX_CSV.name().equals(mode)) {

      List<String> indexPath = args.getOptionValues(INDEX_PATH);
      List<String> exportPath = args.getOptionValues(EXPORT_PATH);
      if (indexPath == null || indexPath.isEmpty()) {
        System.err.println("Missing required parameter --" + INDEX_PATH);
        return;
      }
      if (exportPath == null || exportPath.isEmpty()) {
        System.err.println("Missing required parameter --" + EXPORT_PATH);
        return;
      }
      indexingService.createMainIndexFromFile(exportPath.get(0), indexPath.get(0));
    } else if (Main.ExecutionMode.INDEX_DB.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + CLB_DATASET_ID);
        return;
      }
      indexingService.runDatasetIndexing(Integer.parseInt(datasetIds.get(0)));
    } else if (Main.ExecutionMode.INDEX_IUCN_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + CLB_DATASET_ID);
        return;
      }
      indexingService.indexIUCN(datasetIds.get(0));
    } else if (Main.ExecutionMode.INDEX_IDENTIFIER_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + CLB_DATASET_ID);
        return;
      }
      indexingService.indexIdentifiers(datasetIds.get(0));
    } else {
      System.err.println("Unrecognized mode: " + mode);
    }
  }
}

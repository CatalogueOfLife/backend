package life.catalogue.matching;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("indexing")
public class IndexingApplication implements ApplicationRunner {

  final IndexingService indexingService;
  final DatasetIndex datasetIndex;

  public IndexingApplication(IndexingService indexingService, DatasetIndex datasetIndex) {
    this.indexingService = indexingService;
    this.datasetIndex = datasetIndex;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {

    List<String> modes = args.getOptionValues("mode");
    if (modes == null || modes.isEmpty()) {
      System.err.println("Missing required parameter --mode");
      return;
    }

    String mode = args.getOptionValues("mode").get(0);
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
      List<String> iucnDatasetId = args.getOptionValues(Main.CLB_IUCN_DATASET_ID);
      if (iucnDatasetId != null && !iucnDatasetId.isEmpty()) {
        indexingService.indexIUCN(iucnDatasetId.get(0));
      }

      // build identifier index
      List<String> identifierDatasetIds = args.getOptionValues(Main.CLB_IDENTIFIER_DATASET_IDS);
      if (identifierDatasetIds != null && !identifierDatasetIds.isEmpty()) {
        for (String id : identifierDatasetIds) {
          String[] ids = id.split(",");
          for (String i : ids) {
            indexingService.indexIdentifiers(i);
          }
        }
      }
      System.out.println("Indexing completed");
    } else if (Main.ExecutionMode.EXPORT_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.CLB_DATASET_ID);
        return;
      }
      indexingService.writeCLBToFile(datasetIds.get(0));
    } else if (Main.ExecutionMode.INDEX_CSV.name().equals(mode)) {

      List<String> indexPath = args.getOptionValues(Main.INDEX_PATH);
      List<String> exportPath = args.getOptionValues(Main.EXPORT_PATH);
      if (indexPath == null || indexPath.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.INDEX_PATH);
        return;
      }
      if (exportPath == null || exportPath.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.EXPORT_PATH);
        return;
      }
      indexingService.createMainIndexFromFile(exportPath.get(0), indexPath.get(0));
    } else if (Main.ExecutionMode.INDEX_DB.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.CLB_DATASET_ID);
        return;
      }
      indexingService.runDatasetIndexing(Integer.parseInt(datasetIds.get(0)));
    } else if (Main.ExecutionMode.INDEX_IUCN_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.CLB_DATASET_ID);
        return;
      }
      indexingService.indexIUCN(datasetIds.get(0));
    } else if (Main.ExecutionMode.INDEX_IDENTIFIER_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --" + Main.CLB_DATASET_ID);
        return;
      }
      indexingService.indexIdentifiers(datasetIds.get(0));
    } else {
      System.err.println("Unrecognized mode: " + mode);
    }
  }
}

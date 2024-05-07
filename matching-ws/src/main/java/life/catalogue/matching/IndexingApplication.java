package life.catalogue.matching;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("indexing")
public class IndexingApplication implements ApplicationRunner {

  @Autowired IndexingService indexingService;

  @Override
  public void run(ApplicationArguments args) throws Exception {

    List<String> modes = args.getOptionValues("mode");
    if (modes == null || modes.isEmpty()) {
      System.err.println("Missing required parameter --mode");
      return;
    }

    String mode = args.getOptionValues("mode").get(0);
    List<String> datasetIds = args.getOptionValues("clb.dataset.id");

    if (Main.ExecutionMode.EXPORT_CSV.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --clb.dataset.id");
        return;
      }
      indexingService.writeCLBToFile(datasetIds.get(0));
    } else if (Main.ExecutionMode.INDEX_CSV.name().equals(mode)) {

      List<String> indexPath = args.getOptionValues("index.path");
      List<String> exportPath = args.getOptionValues("export.path");
      if (indexPath == null || indexPath.isEmpty()) {
        System.err.println("Missing required parameter --index.path");
        return;
      }
      if (exportPath == null || exportPath.isEmpty()) {
        System.err.println("Missing required parameter --export.path");
        return;
      }
      indexingService.indexFile(exportPath.get(0), indexPath.get(0));
    } else if (Main.ExecutionMode.INDEX_DB.name().equals(mode)) {
      if (datasetIds == null || datasetIds.isEmpty()) {
        System.err.println("Missing required parameter --clb.dataset.id");
        return;
      }
      indexingService.runDatasetIndexing(Integer.parseInt(datasetIds.get(0)));
    } else {
      System.err.println("Unrecognized mode: " + mode);
    }
  }
}

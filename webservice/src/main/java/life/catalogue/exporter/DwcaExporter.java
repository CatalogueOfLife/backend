package life.catalogue.exporter;

import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobPriority;

import java.io.File;

public class DwcaExporter extends BackgroundJob {

  File archive;
  final int datasetKey;

  DwcaExporter(int datasetKey, int userKey, JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
  }

  public static DwcaExporter dataset(int datasetKey, int userKey) {
    return new DwcaExporter(datasetKey, userKey, JobPriority.MEDIUM);
  }

  @Override
  public void execute() throws Exception {
    exportCore();
    exportExtensions();
    exportMetadata();
    bundle();
  }

  private void bundle() {

  }

  private void exportMetadata() {

  }

  private void exportExtensions() {

  }

  private void exportCore() {
  }

}

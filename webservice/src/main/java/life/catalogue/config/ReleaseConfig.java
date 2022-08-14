package life.catalogue.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import javax.validation.constraints.NotNull;

/**
 *
 */
public class ReleaseConfig {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseConfig.class);
  public boolean restart = false;
  // id start
  public int start = 0;
  // nidx deduplication workaround - should be fixed by now so not enabled by default
  public boolean nidxDeduplication = false;

  @NotNull
  public File reportDir = new File("/tmp/col/release");

  // the COL download directory with monthly and annual subfolder
  public File colDownloadDir = new File("/tmp/col");

  public File reportDir(int datasetKey, int attempt) {
    return new File(reportDir, String.valueOf(datasetKey) + "/" + String.valueOf(attempt));
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return reportDir.mkdirs();
  }

}

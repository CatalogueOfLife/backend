package life.catalogue.config;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ReleaseConfig {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseConfig.class);
  // id to restart with - keep null unless you know what to do
  public Integer restart;
  // nidx deduplication workaround - should be fixed by now so not enabled by default
  public boolean nidxDeduplication = false;

  public boolean deleteOnError = true;

  // project -> list of dataset keys of releases to ignore (e.g. they contain bad ids)
  public Map<Integer, List<Integer>> ignoredReleases;


  // project -> list of dataset keys of releases to also include as a "backup" of the last release
  public Map<Integer, List<Integer>> additionalReleases;

  @NotNull
  public File reportDir = new File("/tmp/col/release");

  // the COL download directory with monthly and annual subfolder
  public File colDownloadDir = new File("/tmp/col");

  public static File reportDir(File reportRoot, int datasetKey, int attempt) {
    return new File(reportDir(reportRoot, datasetKey), String.valueOf(attempt));
  }
  private static File reportDir(File reportRoot, int datasetKey) {
    return new File(reportRoot, String.valueOf(datasetKey));
  }

  public File reportDir(int datasetKey, int attempt) {
    return new File(reportDir(datasetKey), String.valueOf(attempt));
  }

  public File reportDir(int datasetKey) {
    return reportDir(reportDir, datasetKey);
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return reportDir.mkdirs() || colDownloadDir.mkdirs();
  }

}

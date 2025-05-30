package life.catalogue.config;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.NotNull;

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

  // project -> list of action hook URLs to be called after successful releases
  public Map<Integer, List<ReleaseAction>> actions;

  @NotNull
  public File reportDir = new File("/tmp/col/release");

  /**
   * The URI for the directory containing release reports.
   * Warning! The URI MUST end with a slash or otherwise resolved URIs will be wrong!
   */
  @NotNull
  public URI reportURI = URI.create("https://download.checklistbank.org/releases/");

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

  public URI reportURI(int datasetKey, int attempt) {
    return reportURI.resolve(datasetKey + "/" + attempt);
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

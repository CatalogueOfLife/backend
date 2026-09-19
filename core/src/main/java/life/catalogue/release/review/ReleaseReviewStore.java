package life.catalogue.release.review;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.ReleaseConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where a release review lives on disk, and the only place that knows it.
 *
 * A review is two files in the release report directory of the PROJECT - the same {@code <projectKey>/<attempt>}
 * folder the id reports already use, which is published by the download host. There is no database table: the
 * presence of {@code review.html} IS the "a review exists" answer, and deleting a release's reports takes its
 * review with it.
 */
public class ReleaseReviewStore {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseReviewStore.class);
  public static final String REPORT_FILE = "review.html";
  public static final String SIDECAR_FILE = "review.json";

  private final ReleaseConfig cfg;

  public ReleaseReviewStore(ReleaseConfig cfg) {
    this.cfg = cfg;
  }

  public File dir(int projectKey, int attempt) {
    return cfg.reportDir(projectKey, attempt);
  }

  public File report(int projectKey, int attempt) {
    return new File(dir(projectKey, attempt), REPORT_FILE);
  }

  public File sidecar(int projectKey, int attempt) {
    return new File(dir(projectKey, attempt), SIDECAR_FILE);
  }

  public boolean hasReport(int projectKey, int attempt) {
    var f = report(projectKey, attempt);
    return f.exists() && f.length() > 0;
  }

  /**
   * ReleaseConfig.reportURI resolves to a directory without a trailing slash, so the file is appended as a
   * string rather than resolved - resolve() would replace the attempt segment instead of descending into it.
   */
  public URI reportURI(int projectKey, int attempt) {
    return URI.create(cfg.reportURI(projectKey, attempt).toString() + "/" + REPORT_FILE);
  }

  /**
   * @return the persisted review state, or null if this release was never reviewed or the sidecar is unreadable
   */
  public @Nullable ReleaseReviewInfo read(int projectKey, int attempt) {
    File f = sidecar(projectKey, attempt);
    if (!f.exists()) {
      return null;
    }
    try {
      return ApiModule.MAPPER.readValue(f, ReleaseReviewInfo.class);
    } catch (IOException e) {
      // a broken sidecar must not make the review page fail - the report file, if any, is still the truth
      LOG.warn("Failed to read review sidecar {}", f, e);
      return null;
    }
  }

  public void write(int projectKey, int attempt, ReleaseReviewInfo info) throws IOException {
    File dir = dir(projectKey, attempt);
    FileUtils.forceMkdir(dir);
    ApiModule.MAPPER.writerWithDefaultPrettyPrinter().writeValue(sidecar(projectKey, attempt), info);
  }

  public void writeReport(int projectKey, int attempt, byte[] html) throws IOException {
    File dir = dir(projectKey, attempt);
    FileUtils.forceMkdir(dir);
    Files.write(report(projectKey, attempt).toPath(), html);
  }

  public void writeReport(int projectKey, int attempt, String html) throws IOException {
    writeReport(projectKey, attempt, html.getBytes(StandardCharsets.UTF_8));
  }
}

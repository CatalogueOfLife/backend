package life.catalogue.release.review;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.SectorMetricsDiff;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one agentic review of a release as an Anthropic Managed Agents session and stores its HTML report
 * next to the release's other reports.
 *
 * The job does three things the agent cannot do for itself:
 *
 *  1. it mints nothing and stores no password - the resource hands it a short lived JWT for the read-only
 *     review bot, which is rotated into the vault credential for this run;
 *  2. it computes the sector comparison in process and mounts it as a file, because that comparison needs
 *     editor rights over the API and the bot deliberately has none - and because no release can be asked
 *     for its sector sync metrics over the API at all. For a similar reason it mounts the release reports of
 *     both releases, which live on the download host the sandbox cannot reach: the ID reports as they are,
 *     and the job log as a digest, since the log itself runs to several GB;
 *  3. it writes the result to the release report directory, which is what makes a review durable and
 *     publicly linkable.
 *
 * Everything else the agent reads, it reads over the ChecklistBank API with its own read-only credential.
 */
public class ReleaseReviewJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseReviewJob.class);
  static final String PROMPT_RESOURCE = "life/catalogue/release/review-prompt.md";
  static final String METRICS_FILE = "sector-metrics.json";
  /** where the uploaded file is mounted on the session */
  static final String MOUNT_PATH = "/" + METRICS_FILE;
  /** where the agent finds it inside the sandbox */
  static final String SANDBOX_METRICS_PATH = "/mnt/session/uploads/" + METRICS_FILE;
  /** release report files, see IdProvider and JobAppender */
  static final String LOG_FILE = "job.log.gz";
  static final String ID_REPORTS_FILE = "id-reports.gz";
  static final String SANDBOX_UPLOADS = "/mnt/session/uploads/";
  static final String LOG_DIGEST_FILE = "job-log-digest.md";
  static final String ID_REPORTS_DIR = "id-reports";
  static final String PREFIX_PREVIOUS = "previous-";
  /** an ID report bigger than this is left out rather than failing the review */
  static final long MAX_UPLOAD_BYTES = 200L * 1024 * 1024;
  private static final ContentType TEXT = ContentType.create("text/plain", StandardCharsets.UTF_8);
  /** the single file the agent is asked to produce */
  static final String SANDBOX_OUTPUT_PATH = "/mnt/session/outputs/" + ReleaseReviewStore.REPORT_FILE;
  /** how long to keep asking for the output files after the session went idle */
  private static final int OUTPUT_RETRIES = 6;
  private static final int OUTPUT_RETRY_SECONDS = 5;

  private final int releaseKey;
  private final boolean force;
  // never logged, never serialised, never part of getParams()
  private final String botToken;
  private final AiReviewConfig cfg;
  private final ReleaseConfig rCfg;
  private final SqlSessionFactory factory;
  private final CloseableHttpClient http;

  // resolved once the job runs
  private Integer projectKey;
  private Integer attempt;

  public ReleaseReviewJob(int userKey, int releaseKey, boolean force, String botToken,
                          AiReviewConfig cfg, ReleaseConfig rCfg, SqlSessionFactory factory, CloseableHttpClient http) {
    super(userKey);
    this.releaseKey = releaseKey;
    this.force = force;
    this.botToken = botToken;
    this.cfg = cfg;
    this.rCfg = rCfg;
    this.factory = factory;
    this.http = http;
  }

  @Override
  public Integer datasetKey() {
    return releaseKey;
  }

  @Override
  public Object getParams() {
    return Map.of("releaseKey", releaseKey, "force", force);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof ReleaseReviewJob && ((ReleaseReviewJob) other).releaseKey == releaseKey;
  }

  public int getReleaseKey() {
    return releaseKey;
  }

  @Override
  public void execute() throws Exception {
    final var store = new ReleaseReviewStore(rCfg);

    setStep("resolving release");
    final Dataset release;
    final Integer prevKey;
    final Integer prevAttempt;
    final List<Dataset> history;
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      release = dm.get(releaseKey);
      if (release == null || release.hasDeletedDate()) {
        throw NotFoundException.notFound(Dataset.class, releaseKey);
      } else if (release.getOrigin() == null || !release.getOrigin().isRelease()) {
        throw new IllegalArgumentException("Dataset " + releaseKey + " is not a release");
      } else if (release.getAttempt() == null) {
        throw new IllegalArgumentException("Release " + releaseKey + " has no import attempt to store a review with");
      }
      projectKey = release.getSourceKey();
      attempt = release.getAttempt();
      prevKey = dm.previousRelease(releaseKey);
      var prev = prevKey == null ? null : dm.get(prevKey);
      prevAttempt = prev == null ? null : prev.getAttempt();
      history = dm.listReleases(projectKey, false, false);
    }
    if (prevKey == null) {
      throw new IllegalArgumentException("Release " + releaseKey + " has no previous public release of the same kind to compare against");
    }
    if (!force && store.hasReport(projectKey, attempt)) {
      throw new IllegalStateException("Release " + releaseKey + " has been reviewed already");
    }

    final var info = new ReleaseReviewInfo();
    info.setReleaseKey(releaseKey);
    info.setProjectKey(projectKey);
    info.setOrigin(release.getOrigin());
    info.setAttempt(attempt);
    info.setPreviousReleaseKey(prevKey);
    info.setStatus(ReleaseReviewInfo.Status.RUNNING);
    info.setJobKey(getKey());
    info.setModel(cfg.model);
    info.setStarted(LocalDateTime.now());
    store.write(projectKey, attempt, info);

    final var client = new ManagedAgentsClient(cfg, http);

    // the vault substitutes the real secret at egress, so the agent must use it as an opaque bearer token
    setStep("rotating bot credential");
    client.rotateCredential(botToken);

    setStep("comparing sectors");
    List<SectorMetricsDiff> diffs = SectorMetricsComparator.compare(factory, releaseKey, prevKey,
      SectorMetricsComparator.DEFAULT_MIN_CHANGE);
    byte[] metrics = ApiModule.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(diffs);
    LOG.info("Comparing release {} against {} flagged {} of its sectors", releaseKey, prevKey, diffs.size());

    setStep("uploading sector metrics");
    List<ManagedAgentsClient.Mount> mounts = new ArrayList<>();
    mounts.add(new ManagedAgentsClient.Mount(
      client.uploadFile(METRICS_FILE, metrics, ContentType.APPLICATION_JSON), MOUNT_PATH));

    // what got mounted, and what was missing, is listed in the prompt
    StringBuilder reports = new StringBuilder();
    setStep("digesting release logs");
    final File reportDir = rCfg.reportDir(projectKey, attempt);
    final File prevReportDir = prevAttempt == null ? null : rCfg.reportDir(projectKey, prevAttempt);
    mountLogDigest(client, mounts, reports, "this release", reportDir, "");
    mountLogDigest(client, mounts, reports, "the previous release", prevReportDir, PREFIX_PREVIOUS);
    setStep("uploading ID reports");
    mountIdReports(client, mounts, reports, "this release", reportDir, "");
    mountIdReports(client, mounts, reports, "the previous release", prevReportDir, PREFIX_PREVIOUS);

    setStep("starting session");
    String task = renderPrompt(release, prevKey, history, reports.toString().strip());
    String sessionId = client.createSession(task, mounts);
    info.setSessionId(sessionId);
    store.write(projectKey, attempt, info);

    setStep("waiting for the agent");
    var state = awaitSession(client, sessionId);
    info.setListCost(state.listCost);
    LOG.info("Managed agents session {} for release {} ended as {}", sessionId, releaseKey, state);

    setStep("fetching report");
    byte[] html = fetchReport(client, sessionId);
    store.writeReport(projectKey, attempt, html);

    info.setStatus(ReleaseReviewInfo.Status.FINISHED);
    info.setFinished(LocalDateTime.now());
    info.setError(null);
    store.write(projectKey, attempt, info);
    LOG.info("Wrote release review of {} to {}", releaseKey, store.report(projectKey, attempt));
  }

  /**
   * Mounts a digest of a release's job log. Streaming the gzipped log takes a minute or two for a big release,
   * but the log itself is several GB and far too much for the agent to read.
   */
  static void mountLogDigest(ManagedAgentsClient client, List<ManagedAgentsClient.Mount> mounts, StringBuilder reports,
                             String which, @Nullable File reportDir, String prefix) throws IOException {
    File log = reportDir == null ? null : new File(reportDir, LOG_FILE);
    if (log == null || !log.exists()) {
      reports.append("- **not available:** the job log of ").append(which)
        .append(" - there is no ").append(LOG_FILE).append(" in its report directory\n");
      return;
    }
    LOG.info("Digesting release log {}", log);
    byte[] digest = ReleaseLogDigest.digest(log).getBytes(StandardCharsets.UTF_8);
    String name = prefix + LOG_DIGEST_FILE;
    mounts.add(new ManagedAgentsClient.Mount(client.uploadFile(name, digest, TEXT), "/" + name));
    reports.append("- `").append(SANDBOX_UPLOADS).append(name).append("` - the job log digest of ").append(which).append('\n');
  }

  /**
   * Mounts the entries of a release's ID reports as plain text files, so the agent needs no unzip -
   * id-reports.gz is a zip archive despite its name.
   */
  static void mountIdReports(ManagedAgentsClient client, List<ManagedAgentsClient.Mount> mounts, StringBuilder reports,
                             String which, @Nullable File reportDir, String prefix) throws IOException {
    File zip = reportDir == null ? null : new File(reportDir, ID_REPORTS_FILE);
    if (zip == null || !zip.exists()) {
      reports.append("- **not available:** the ID reports of ").append(which)
        .append(" - there is no ").append(ID_REPORTS_FILE).append(" in its report directory\n");
      return;
    }
    final String dir = prefix + ID_REPORTS_DIR;
    List<String> mounted = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    try (var zf = new ZipFile(zip)) {
      for (var entry : Collections.list(zf.entries())) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = new File(entry.getName()).getName();
        if (entry.getSize() > MAX_UPLOAD_BYTES) {
          LOG.warn("Skip ID report {} of {} bytes in {}", name, entry.getSize(), zip);
          skipped.add(name + " (too big)");
          continue;
        }
        byte[] content;
        try (var in = zf.getInputStream(entry)) {
          content = in.readAllBytes();
        }
        // an empty report says nothing a count of zero would not
        if (content.length == 0) {
          skipped.add(name + " (empty)");
          continue;
        }
        mounts.add(new ManagedAgentsClient.Mount(client.uploadFile(dir + "-" + name, content, TEXT), "/" + dir + "/" + name));
        mounted.add(name);
      }
    }
    reports.append("- `").append(SANDBOX_UPLOADS).append(dir).append("/` - the ID reports of ").append(which)
      .append(": ").append(mounted.isEmpty() ? "none" : String.join(", ", mounted));
    if (!skipped.isEmpty()) {
      reports.append("; not mounted: ").append(String.join(", ", skipped));
    }
    reports.append('\n');
  }

  /**
   * Records the failure in the sidecar and, crucially, leaves no review.html behind, so the release can simply
   * be reviewed again. Catches Throwable because BackgroundJob does - a review killed by an Error must not look
   * like one still running.
   */
  @Override
  protected void onError(Throwable e) {
    if (projectKey == null || attempt == null) {
      // we never got far enough to know where the sidecar goes
      return;
    }
    try {
      var store = new ReleaseReviewStore(rCfg);
      var info = store.read(projectKey, attempt);
      if (info == null) {
        info = new ReleaseReviewInfo();
        info.setReleaseKey(releaseKey);
        info.setProjectKey(projectKey);
        info.setAttempt(attempt);
        info.setJobKey(getKey());
        info.setModel(cfg.model);
      }
      info.setStatus(ReleaseReviewInfo.Status.FAILED);
      info.setFinished(LocalDateTime.now());
      info.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
      store.write(projectKey, attempt, info);
    } catch (Exception ex) {
      LOG.error("Failed to record the failed review of release {}", releaseKey, ex);
    }
  }

  private ManagedAgentsClient.SessionState awaitSession(ManagedAgentsClient client, String sessionId) throws Exception {
    final long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(cfg.timeoutMinutes);
    ManagedAgentsClient.SessionState state = null;
    while (System.currentTimeMillis() < deadline) {
      checkIfCancelled();
      TimeUnit.SECONDS.sleep(cfg.pollSeconds);
      state = client.getSession(sessionId);
      // the raw status is logged verbatim: this is a beta API and an unknown value is exactly what we need to see
      LOG.debug("Managed agents session {} status={}", sessionId, state.status);
      if (state.isTerminal()) {
        return state;
      }
    }
    throw new IllegalStateException("Managed agents session " + sessionId + " did not finish within "
      + cfg.timeoutMinutes + " minutes, last status " + (state == null ? "unknown" : state.status));
  }

  /**
   * Output files appear a few seconds after a session goes idle, so an empty listing is retried rather than
   * treated as a failed review.
   */
  private byte[] fetchReport(ManagedAgentsClient client, String sessionId) throws Exception {
    for (int i = 0; i < OUTPUT_RETRIES; i++) {
      checkIfCancelled();
      var files = client.listSessionFiles(sessionId);
      var report = pickReport(files);
      if (report != null) {
        byte[] html = client.downloadFile(report.id);
        if (html.length > 0) {
          return html;
        }
        LOG.warn("Session {} produced an empty {}", sessionId, report.filename);
      }
      TimeUnit.SECONDS.sleep(OUTPUT_RETRY_SECONDS);
    }
    throw new IllegalStateException("Managed agents session " + sessionId + " produced no " + ReleaseReviewStore.REPORT_FILE);
  }

  /**
   * Prefers the file the prompt asked for, but accepts a single html file under another name rather than
   * throwing away a finished review over its filename.
   */
  @Nullable
  static ManagedAgentsClient.FileInfo pickReport(List<ManagedAgentsClient.FileInfo> files) {
    for (var f : files) {
      if (f.filename != null && f.filename.toLowerCase().endsWith(ReleaseReviewStore.REPORT_FILE)) {
        return f;
      }
    }
    var html = files.stream()
      .filter(f -> f.filename != null && f.filename.toLowerCase().endsWith(".html"))
      .toList();
    return html.size() == 1 ? html.get(0) : null;
  }

  private String renderPrompt(Dataset release, int prevKey, List<Dataset> history, String releaseReports) throws Exception {
    String prevAlias = history.stream()
      .filter(d -> d.getKey() != null && d.getKey() == prevKey)
      .map(ReleaseReviewJob::label)
      .findFirst()
      .orElse("#" + prevKey);

    return Resources.toString(PROMPT_RESOURCE)
      .replace("{{releaseKey}}", String.valueOf(releaseKey))
      .replace("{{releaseAlias}}", label(release))
      .replace("{{projectKey}}", String.valueOf(projectKey))
      .replace("{{origin}}", release.getOrigin().name())
      .replace("{{attempt}}", String.valueOf(attempt))
      .replace("{{previousReleaseKey}}", String.valueOf(prevKey))
      .replace("{{previousReleaseAlias}}", prevAlias)
      .replace("{{releaseHistory}}", historyTable(history))
      .replace("{{apiURI}}", stripTrailingSlash(cfg.apiURI.toString()))
      .replace("{{clbURI}}", stripTrailingSlash(cfg.clbURI.toString()))
      .replace("{{secretName}}", cfg.secretName)
      .replace("{{sectorMetricsPath}}", SANDBOX_METRICS_PATH)
      .replace("{{reportsURI}}", rCfg.reportURI(projectKey, attempt).toString())
      .replace("{{releaseReports}}", releaseReports)
      .replace("{{outputPath}}", SANDBOX_OUTPUT_PATH);
  }

  private static String stripTrailingSlash(String uri) {
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  private static String label(Dataset d) {
    String alias = d.getAlias() != null ? d.getAlias() : d.getTitle();
    return alias == null ? "#" + d.getKey() : alias;
  }

  /**
   * A markdown table of the project's public releases, newest first, both base and extended, so the agent can
   * tell a one-off anomaly from a trend.
   */
  private static String historyTable(List<Dataset> history) {
    StringBuilder sb = new StringBuilder("| key | alias | origin | issued | created |\n|---|---|---|---|---|\n");
    history.stream()
      .filter(d -> d.getOrigin() != null && d.getOrigin().isRelease())
      .sorted(Comparator.comparing(Dataset::getKey, Comparator.reverseOrder()))
      .limit(24)
      .forEach(d -> sb.append("| `").append(d.getKey()).append("` | ")
        .append(label(d)).append(" | ")
        .append(d.getOrigin()).append(" | ")
        .append(d.getIssued() == null ? "" : d.getIssued()).append(" | ")
        .append(d.getCreated() == null ? "" : d.getCreated().toLocalDate()).append(" |\n"));
    return sb.toString();
  }
}

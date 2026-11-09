package life.catalogue.jobs;

import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.ReleaseRanking;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMapper.ArchivedSimpleName;
import life.catalogue.db.mapper.ArchivedNameUsageMapper.Redirect;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.release.IgnoredReleases;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.IntFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Backfills the superseded redirects of a project's name usage archive: every archived id that no live release carries
 * and that has no redirect yet is paired with the live ids of its own canonical group, see {@link RedirectPairing}, and
 * points at the best of them if the evidence is good enough.
 *
 * Publishing a release only records redirects for the ids the previous release still had, so ids that were dropped
 * before redirects existed, or by releases whose id assignment was broken (COL26.7 to 26.9, data#1867), never got one.
 * This job fills them in afterwards. It never overwrites an existing redirect, so running it again changes nothing.
 *
 * Every decision, redirect or not, goes into a tab separated report that is the job's result file. A dry run writes
 * nothing but the report. It holds the project's dataset lock like the release jobs, but publishing a release archives
 * it without that lock: the job therefore checks before every write that the live releases are still the same, and
 * fails otherwise.
 */
public class ArchiveRedirectJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveRedirectJob.class);
  static final int BATCH_SIZE = 1000;
  static final String REPORT_NAME = "redirects.tsv";
  private static final String[] HEADER = {"deadID", "deadLabel", "deadLastRelease", "liveID", "liveLabel", "verdict", "outcome"};

  private final SqlSessionFactory factory;
  private final IntFunction<IntSet> ignoredReleases;
  private final @Nullable Integer since;
  private final boolean dryRun;
  private final JobResult result;
  private final Map<RedirectPairing.Outcome, Integer> counts = new EnumMap<>(RedirectPairing.Outcome.class);
  private int updated;

  public record Params(int projectKey, @Nullable Integer since, boolean dryRun) {
  }

  /**
   * @param since key of a release of the project. If given only ids carried by this release or a later one, by attempt,
   *              get a redirect. If null every dropped id does.
   */
  public ArchiveRedirectJob(int userKey, SqlSessionFactory factory, int projectKey, @Nullable Integer since, boolean dryRun) {
    this(userKey, factory, new IgnoredReleases(factory), projectKey, since, dryRun);
  }

  ArchiveRedirectJob(int userKey, SqlSessionFactory factory, IntFunction<IntSet> ignoredReleases, int projectKey,
                     @Nullable Integer since, boolean dryRun) {
    super(projectKey, userKey, JobPriority.HIGH);
    DaoUtils.requireProject(projectKey, "Only projects have a name usage archive");
    this.factory = factory;
    this.ignoredReleases = ignoredReleases;
    this.since = since;
    this.dryRun = dryRun;
    this.result = new JobResult(getKey());
    this.logToFile = true;
    if (since != null) {
      try (SqlSession session = factory.openSession(true)) {
        if (session.getMapper(DatasetMapper.class).listReleasesForArchive(projectKey).stream().noneMatch(r -> r.getKey() == since)) {
          throw new IllegalArgumentException("Dataset " + since + " is no public release of project " + projectKey);
        }
      }
    }
  }

  @Override
  public Object getParams() {
    return new Params(datasetKey, since, dryRun);
  }

  @Override
  public JobResult getResult() {
    return result;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    // a dry run only reports, it is no duplicate of a real run of the same project
    return other instanceof ArchiveRedirectJob job && job.datasetKey == datasetKey && job.dryRun == dryRun
           && Objects.equals(job.since, since);
  }

  /**
   * The live and the dead releases of the project, as of now.
   */
  record Keys(List<Integer> live, IntSet liveBase, List<Integer> dead) {
  }

  Keys keys() {
    final List<ArchivableRelease> releases;
    try (SqlSession session = factory.openSession(true)) {
      releases = session.getMapper(DatasetMapper.class).listReleasesForArchive(datasetKey);
    }
    final IntSet ignored = ignoredReleases.apply(datasetKey);
    final var ranking = new ReleaseRanking(datasetKey, releases, ignored);
    final List<Integer> live = ranking.liveKeys();
    final IntSet liveBase = new IntOpenHashSet();
    Integer sinceAttempt = null;
    for (var r : releases) {
      if (live.contains(r.getKey()) && r.getOrigin() == DatasetOrigin.RELEASE) {
        liveBase.add(r.getKey());
      }
      if (since != null && r.getKey() == since) {
        sinceAttempt = attempt(r);
      }
    }
    if (since != null && sinceAttempt == null) {
      throw new IllegalArgumentException("Dataset " + since + " is no public release of project " + datasetKey);
    }
    // deleted releases included: their ids are archived as well. Ignored releases never published an id we redirect
    List<Integer> dead = new ArrayList<>();
    for (var r : releases) {
      if (!ignored.contains(r.getKey()) && (sinceAttempt == null || attempt(r) >= sinceAttempt)) {
        dead.add(r.getKey());
      }
    }
    return new Keys(live, liveBase, dead);
  }

  private static int attempt(ArchivableRelease r) {
    return r.getAttempt() == null ? 0 : r.getAttempt();
  }

  @Override
  protected void runWithLock() throws Exception {
    final Keys keys = keys();
    LOG.info("{} superseded redirects of project {} with live releases {} for ids of releases {}",
      dryRun ? "Report" : "Backfill", datasetKey, keys.live, keys.dead);
    if (keys.live.isEmpty() || keys.dead.isEmpty()) {
      LOG.warn("Project {} has no live or no dead releases, nothing to redirect", datasetKey);
    }
    setStep(dryRun ? "reporting redirects" : "backfilling redirects");
    final var pairing = new RedirectPairing(new TaxGroupAnalyzer(), new IntOpenHashSet(keys.live), keys.liveBase);
    final Map<Integer, Integer> attempts = releaseAttempts();
    final File tmp = new File(result.getFile().getParentFile(), result.getFile().getName() + ".tmp");
    Files.createDirectories(tmp.getParentFile().toPath());
    try (var zos = new ZipOutputStream(new FileOutputStream(tmp));
         SqlSession session = factory.openSession(false)
    ) {
      zos.putNextEntry(new ZipEntry(REPORT_NAME));
      final Writer w = new BufferedWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
      writeRow(w, HEADER);
      final List<Redirect> batch = new ArrayList<>();
      final List<ArchivedSimpleName> group = new ArrayList<>();
      final Integer[] groupId = {null};
      if (!keys.live.isEmpty() && !keys.dead.isEmpty()) {
        PgUtils.consume(
          () -> session.getMapper(ArchivedNameUsageMapper.class).processRedirectCandidates(datasetKey, keys.live, keys.dead),
          sn -> {
            if (!group.isEmpty() && !Objects.equals(groupId[0], sn.getNamesIndexId())) {
              processGroup(pairing, group, attempts, w, batch, keys);
            }
            groupId[0] = sn.getNamesIndexId();
            group.add(sn);
          }
        );
        processGroup(pairing, group, attempts, w, batch, keys);
        flush(batch, keys);
      }
      w.flush();
      zos.closeEntry();
    }
    Files.move(tmp.toPath(), result.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    result.calculateSizeAndMd5();
    LOG.info("{} superseded redirects of project {}: {}. Report at {}", dryRun ? "Reported" : "Backfilled", datasetKey,
      summary(), result.getFile());
  }

  private Map<Integer, Integer> releaseAttempts() {
    Map<Integer, Integer> attempts = new HashMap<>();
    try (SqlSession session = factory.openSession(true)) {
      for (var r : session.getMapper(DatasetMapper.class).listReleasesForArchive(datasetKey)) {
        attempts.put(r.getKey(), attempt(r));
      }
    }
    return attempts;
  }

  private void processGroup(RedirectPairing pairing, List<ArchivedSimpleName> group, Map<Integer, Integer> attempts,
                            Writer w, List<Redirect> batch, Keys keys) {
    try {
      for (var p : pairing.pair(group)) {
        counts.merge(p.outcome(), 1, Integer::sum);
        writeRow(w, new String[]{
          p.dead().getId(),
          p.dead().getLabel(),
          str(lastRelease(p.dead(), attempts)),
          p.live() == null ? null : p.live().getId(),
          p.live() == null ? null : p.live().getLabel(),
          str(p.verdict()),
          p.outcome().name()
        });
        if (p.isRedirect()) {
          batch.add(new Redirect(p.dead().getId(), p.live().getId()));
          if (batch.size() >= BATCH_SIZE) {
            flush(batch, keys);
          }
        }
      }
      group.clear();
      checkIfCancelled();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /**
   * Writes the buffered redirects, unless this is a dry run, after making sure the live releases did not change.
   */
  private void flush(List<Redirect> batch, Keys keys) {
    if (batch.isEmpty()) {
      return;
    }
    if (!dryRun) {
      var now = keys();
      if (!now.live.equals(keys.live)) {
        throw new IllegalStateException("The live releases of project " + datasetKey + " changed from " + keys.live
          + " to " + now.live + " while redirecting, a release must have been published. Run the job again");
      }
      try (SqlSession session = factory.openSession(true)) {
        updated += session.getMapper(ArchivedNameUsageMapper.class).fillSuperseded(datasetKey, batch, keys.live);
      }
    }
    batch.clear();
  }

  /**
   * @return the key of the release with the highest attempt that carried the id
   */
  @Nullable
  static Integer lastRelease(ArchivedSimpleName sn, Map<Integer, Integer> attempts) {
    Integer last = null;
    if (sn.getReleaseKeys() != null) {
      for (int k : sn.getReleaseKeys()) {
        if (last == null || attempts.getOrDefault(k, 0) > attempts.getOrDefault(last, 0)) {
          last = k;
        }
      }
    }
    return last;
  }

  private static String str(@Nullable Object o) {
    return o == null ? null : o.toString();
  }

  private static void writeRow(Writer w, String[] row) throws IOException {
    for (int i = 0; i < row.length; i++) {
      if (i > 0) {
        w.write('\t');
      }
      if (row[i] != null) {
        w.write(row[i].replaceAll("[\t\r\n]+", " "));
      }
    }
    w.write('\n');
  }

  String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append(dryRun ? "dry run, nothing written: " : updated + " redirects written: ");
    boolean first = true;
    for (var o : RedirectPairing.Outcome.values()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(counts.getOrDefault(o, 0)).append(' ').append(o.name().toLowerCase().replace('_', ' '));
      first = false;
    }
    return sb.toString();
  }

  public Map<RedirectPairing.Outcome, Integer> getCounts() {
    return counts;
  }

  public int getUpdated() {
    return updated;
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // a successful job's step is cleared before onFinish runs, so the outcome has to be set here to be kept
    if (getStatus() == JobStatus.FINISHED) {
      setStep(summary());
    }
  }
}

package life.catalogue.jobs;

import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.ArchiveStats;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.matching.ArchiveMatcher;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.IgnoredReleases;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Refreshes the name usage archive of one project in place, so every archived id holds the version of its highest
 * ranked release, then rematches the archived names through the names index. It never deletes an archive record.
 *
 * It holds the project's dataset lock, the lock base and extended release jobs take, so no release of the project maps
 * ids meanwhile. A dry run only counts what would be written. See docs/2026-09-15-name-usage-archive-migration.md.
 */
public class ArchiveRefreshJob extends DatasetBlockingJob {
  private final SqlSessionFactory factory;
  private final NameIndex ni;
  private final boolean dryRun;
  private ArchiveStats stats;
  private int rematched;

  public record Params(int projectKey, boolean dryRun) {
  }

  public ArchiveRefreshJob(int userKey, SqlSessionFactory factory, NameIndex ni, int projectKey, boolean dryRun) {
    super(projectKey, userKey, JobPriority.HIGH);
    DaoUtils.requireProject(projectKey, "Only projects have a name usage archive");
    this.factory = factory;
    this.ni = ni.assertOnline();
    this.dryRun = dryRun;
    this.logToFile = true;
  }

  @Override
  public Object getParams() {
    return new Params(datasetKey, dryRun);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    // a dry run only counts, it is no duplicate of a real run of the same project
    return other instanceof ArchiveRefreshJob job && job.datasetKey == datasetKey && job.dryRun == dryRun;
  }

  @Override
  protected void runWithLock() throws Exception {
    var archiver = new NameUsageArchiver(factory, new IgnoredReleases(factory));
    setStep(dryRun ? "counting changes" : "refreshing archive records");
    stats = archiver.archiveProject(archiver.ranking(datasetKey), false, dryRun);
    if (!dryRun) {
      checkIfCancelled();
      setStep("rematching archived names");
      var matcher = new ArchiveMatcher(factory, ni);
      matcher.match(datasetKey);
      rematched = matcher.getUpdated();
    }
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // a successful job's step is cleared before onFinish runs, so the outcome has to be set here to be kept
    if (getStatus() == JobStatus.FINISHED && stats != null) {
      setStep(dryRun ? "dry run, nothing written: " + stats : stats + ", " + rematched + " archive matches changed by the names index");
    }
  }
}

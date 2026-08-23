package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

/**
 * Removes sector import metrics and their names files that neither a release nor the project pins,
 * and that predate the projects most recent release.
 * Every attempt referenced by some sector.sync_attempt - the projects current sync and each releases
 * pinned sync - is kept, so no release or source metric changes.
 * Empty names files are removed for kept rows too, as they carry nothing the metrics row does not.
 */
public class SectorImportRetentionJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(SectorImportRetentionJob.class);
  // Page size for the keyset-paginated listAttempts() reads, and - since the two happen to coincide - also the
  // batch size of one delete transaction: a page is classified with no session open, then its doomed rows
  // (at most PAGE of them) are deleted together in flush(). One constant serves both purposes.
  private static final int PAGE = 5000;

  private final SqlSessionFactory factory;
  private final FileMetricsSectorDao fmDao;
  private final boolean dryRun;

  @JsonProperty
  private int examined;
  @JsonProperty
  private int deletableRows;
  /**
   * Populated in a dry run only - the number of names files a real run would delete. Always 0 in a real
   * run; do not read this to check whether a real run had files to delete, see {@link #dryRun}.
   */
  @JsonProperty
  private int deletableFiles;
  @JsonProperty
  private int deletedRows;
  /**
   * Populated in a real run only - the number of names files actually deleted. Always 0 in a dry run;
   * do not read this to check whether a dry run found files to delete, see {@link #dryRun}.
   */
  @JsonProperty
  private int deletedFiles;
  /**
   * Real run only: names files whose deletion was attempted but failed (e.g. NFS EACCES/ESTALE/EIO, a
   * read-only export). Counted separately from deletedFiles so one bad file does not abort or misreport
   * a multi-hour run - see the per-file try/catch in {@link #deleteFile}.
   */
  @JsonProperty
  private int failedFiles;

  public SectorImportRetentionJob(int userKey, SqlSessionFactory factory, FileMetricsSectorDao fmDao,
                                  int projectKey, boolean dryRun) {
    super(projectKey, userKey, JobPriority.LOW);
    this.factory = factory;
    this.fmDao = fmDao;
    this.dryRun = dryRun;
  }

  public int getExamined() {
    return examined;
  }

  public int getDeletableRows() {
    return deletableRows;
  }

  public int getDeletableFiles() {
    return deletableFiles;
  }

  public int getDeletedRows() {
    return deletedRows;
  }

  public int getDeletedFiles() {
    return deletedFiles;
  }

  public int getFailedFiles() {
    return failedFiles;
  }

  @JsonProperty
  public boolean isDryRun() {
    return dryRun;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof SectorImportRetentionJob
           && ((SectorImportRetentionJob) other).datasetKey == datasetKey;
  }

  @Override
  protected void runWithLock() throws Exception {
    final LocalDateTime cutoff;
    final Set<Long> pinned = new HashSet<>();

    try (SqlSession session = factory.openSession(true)) {
      var sim = session.getMapper(SectorImportMapper.class);
      cutoff = sim.lastReleaseCreated(datasetKey);
      if (cutoff == null) {
        LOG.warn("Project {} has no release, so there is no safe retention cutoff. Nothing to do.", datasetKey);
        return;
      }
      for (var p : sim.listPinnedAttempts(datasetKey)) {
        pinned.add(pinKey(p.getSectorKey(), p.getAttempt()));
      }
      LOG.info("Retention for project {}: cutoff {}, {} pinned attempts", datasetKey, cutoff, pinned.size());
    }

    // Keyset pagination over the sector_import primary key (sector_key, attempt): each page is fetched in
    // its own short, autocommit session that is closed (see the try-with-resources below) before any
    // filesystem work happens for that page's rows. So the only transactions this job ever holds open are
    // (a) the short autocommit read above for cutoff/pinned, (b) one short autocommit read per page here,
    // and (c) the short read-write transaction flush() opens per page to delete that page's doomed rows -
    // none of them spans a stat or a file delete, which matters once the names files live on slow NFS.
    Integer lastSectorKey = null;
    Integer lastAttempt = null;
    while (true) {
      // Checked once per page, here at the very top of the loop: no transaction is open and no partial
      // batch is pending at this point (the previous page's flush(), including its file deletes, has
      // already completed) - so a cancellation lands cleanly between pages instead of orphaning a batch
      // or a half-open transaction. This is the only place in the loop where the destructive work can be
      // interrupted, since DatasetBlockingJob.execute() only checks before runWithLock() starts.
      checkIfCancelled();

      // listAttempts' keyset params must be both null or both non-null - see its javadoc. A half-null pair
      // would make the keyset predicate evaluate to NULL for every row, so the next page would silently
      // come back empty and the loop would break as if the scan had reached the end - having actually
      // examined nothing beyond this point.
      Preconditions.checkArgument((lastSectorKey == null) == (lastAttempt == null),
        "afterSectorKey and afterAttempt must both be null or both non-null");
      final List<SectorImportMapper.AttemptInfo> page;
      try (SqlSession session = factory.openSession(true)) {
        page = session.getMapper(SectorImportMapper.class).listAttempts(datasetKey, lastSectorKey, lastAttempt, PAGE);
      }
      if (page.isEmpty()) {
        break;
      }

      // No session is open from here until flush() below: classification and all filesystem work for this
      // page happen with no transaction held.
      final List<SectorImportMapper.AttemptInfo> doomed = new ArrayList<>();
      for (var a : page) {
        examined++;
        // SectorSync extends BackgroundJob, not DatasetBlockingJob, so this job's dataset lock does not
        // exclude a running sector sync. SectorRunnable inserts its sector_import row up front but only
        // persists `started` in its finally block, so a queued or running sync has started = NULL in the
        // database for its whole lifetime and must be kept here, or its row could be deleted mid-flight.
        boolean keep = pinned.contains(pinKey(a.getSectorKey(), a.getAttempt()))
                       || a.getStarted() == null
                       || !a.getStarted().isBefore(cutoff);
        if (keep) {
          // zero-name attempts write no names file at all, so only count/delete a file that actually exists
          boolean empty = a.getNameCount() != null && a.getNameCount() == 0;
          if (empty) {
            countDeletableFile(a);
            deleteFile(a);
          }
        } else {
          doomed.add(a);
          deletableRows++;
          countDeletableFile(a);
        }
        if (examined % 500_000 == 0) {
          LOG.info("Retention for project {}: examined {}, deletable {}", datasetKey, examined, deletableRows);
        }
      }
      // Deletes this page's doomed rows in their own short transaction (a no-op in dry run).
      flush(doomed);

      var lastOfPage = page.get(page.size() - 1);
      lastSectorKey = lastOfPage.getSectorKey();
      lastAttempt = lastOfPage.getAttempt();
      if (page.size() < PAGE) {
        break;
      }
    }

    LOG.info("Retention for project {} {}: examined {}, rows {} {}, files {} {}, failedFiles {}",
      datasetKey, dryRun ? "DRY RUN" : "done", examined,
      dryRun ? deletableRows : deletedRows, dryRun ? "deletable" : "deleted",
      dryRun ? deletableFiles : deletedFiles, dryRun ? "deletable" : "deleted", failedFiles);
  }

  private static long pinKey(int sectorKey, int attempt) {
    return ((long) sectorKey << 32) | (attempt & 0xffffffffL);
  }

  private void flush(List<SectorImportMapper.AttemptInfo> batch) {
    if (batch.isEmpty()) {
      return;
    }
    if (!dryRun) {
      // Short, self-contained read-write transaction: opened, committed and closed here, with no
      // filesystem work inside it - the doomed files below are only touched after this session is closed.
      int[] sectorKeys = new int[batch.size()];
      int[] attempts = new int[batch.size()];
      for (int i = 0; i < batch.size(); i++) {
        sectorKeys[i] = batch.get(i).getSectorKey();
        attempts[i] = batch.get(i).getAttempt();
      }
      try (SqlSession session = factory.openSession(false)) {
        deletedRows += session.getMapper(SectorImportMapper.class).deleteAttempts(datasetKey, sectorKeys, attempts);
        session.commit();
      }
      for (var a : batch) {
        deleteFile(a);
      }
    }
  }

  /**
   * Real run only: deletes the names file for a candidate, using the delete call itself as the existence
   * check (one NFS round trip) rather than stat'ing first. A single file that cannot be removed (e.g. an
   * NFS EACCES/ESTALE/EIO, a read-only export) is logged and counted in failedFiles rather than aborting
   * the run - a multi-hour deletion must not die on one bad file.
   */
  private void deleteFile(SectorImportMapper.AttemptInfo a) {
    if (!dryRun) {
      var key = DSID.of(datasetKey, a.getSectorKey());
      try {
        if (fmDao.deleteNamesFileIfExists(key, a.getAttempt())) {
          deletedFiles++;
        }
      } catch (IOException e) {
        failedFiles++;
        LOG.warn("Failed to delete names file {}", fmDao.namesFile(key, a.getAttempt()), e);
      }
    }
  }

  /**
   * Dry run only: a stat per candidate is unavoidable here since nothing may actually be deleted.
   * Mirrors what deleteFile() would do in a real run, so deletableFiles stays a faithful prediction
   * of deletedFiles.
   */
  private void countDeletableFile(SectorImportMapper.AttemptInfo a) {
    if (dryRun) {
      var key = DSID.of(datasetKey, a.getSectorKey());
      if (fmDao.namesFile(key, a.getAttempt()).exists()) {
        deletableFiles++;
      }
    }
  }
}

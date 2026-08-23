package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportMapper;

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

/**
 * Removes sector import metrics and their names files that neither a release nor the project pins,
 * and that predate the projects most recent release.
 * Every attempt referenced by some sector.sync_attempt - the projects current sync and each releases
 * pinned sync - is kept, so no release or source metric changes.
 * Empty names files are removed for kept rows too, as they carry nothing the metrics row does not.
 */
public class SectorImportRetentionJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(SectorImportRetentionJob.class);
  private static final int BATCH = 5000;

  private final SqlSessionFactory factory;
  private final FileMetricsSectorDao fmDao;
  private final boolean dryRun;

  @JsonProperty
  private int examined;
  @JsonProperty
  private int deletableRows;
  @JsonProperty
  private int deletableFiles;
  @JsonProperty
  private int deletedRows;
  @JsonProperty
  private int deletedFiles;

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

    final List<SectorImportMapper.AttemptInfo> doomed = new ArrayList<>();
    final List<SectorImportMapper.AttemptInfo> emptyKept = new ArrayList<>();

    try (SqlSession session = factory.openSession(true);
         var cursor = session.getMapper(SectorImportMapper.class).processAttempts(datasetKey)) {
      for (var a : cursor) {
        examined++;
        boolean keep = pinned.contains(pinKey(a.getSectorKey(), a.getAttempt()))
                       || a.getStarted() == null
                       || !a.getStarted().isBefore(cutoff);
        if (keep) {
          if (a.getNameCount() != null && a.getNameCount() == 0) {
            emptyKept.add(a);
            deletableFiles++;
          }
        } else {
          doomed.add(a);
          deletableRows++;
          deletableFiles++;
          if (doomed.size() >= BATCH) {
            flush(doomed);
          }
        }
        if (examined % 500_000 == 0) {
          LOG.info("Retention for project {}: examined {}, deletable {}", datasetKey, examined, deletableRows);
        }
      }
    }
    flush(doomed);
    // kept rows whose names file is empty still lose the file
    for (var a : emptyKept) {
      deleteFile(a);
    }

    LOG.info("Retention for project {} {}: examined {}, rows {} {}, files {} {}",
      datasetKey, dryRun ? "DRY RUN" : "done", examined,
      dryRun ? deletableRows : deletedRows, dryRun ? "deletable" : "deleted",
      dryRun ? deletableFiles : deletedFiles, dryRun ? "deletable" : "deleted");
  }

  private static long pinKey(int sectorKey, int attempt) {
    return ((long) sectorKey << 32) | (attempt & 0xffffffffL);
  }

  private void flush(List<SectorImportMapper.AttemptInfo> batch) {
    if (batch.isEmpty()) {
      return;
    }
    if (!dryRun) {
      try (SqlSession session = factory.openSession(false)) {
        deletedRows += session.getMapper(SectorImportMapper.class).deleteAttempts(datasetKey, batch);
        session.commit();
      }
      for (var a : batch) {
        deleteFile(a);
      }
    }
    batch.clear();
  }

  private void deleteFile(SectorImportMapper.AttemptInfo a) {
    if (!dryRun) {
      fmDao.deleteAttempt(DSID.of(datasetKey, a.getSectorKey()), a.getAttempt());
      deletedFiles++;
    }
  }
}

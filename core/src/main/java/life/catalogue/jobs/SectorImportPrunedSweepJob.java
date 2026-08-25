package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportPrunedMapper;
import life.catalogue.db.mapper.SectorImportPrunedMapper.PrunedAttempt;

import java.io.IOException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes the names files of the sector syncs the unified job migration pruned.
 * <p>
 * That migration removes sector_import rows in SQL, which is what makes the job backfill affordable, but
 * file cleanup is driven by those rows - once they are gone nothing points at the files any more. It
 * therefore records what it deleted in sector_import_pruned, and this job works through that table.
 * <p>
 * A real run deletes each swept page from sector_import_pruned, so it is resumable and leaves the table
 * empty; drop the table once it has run. A dry run only reports what it would remove and changes nothing.
 */
public class SectorImportPrunedSweepJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(SectorImportPrunedSweepJob.class);
  // one page is read, its files deleted with no session open, then the page is dropped from the table
  private static final int PAGE = 5000;

  private final SqlSessionFactory factory;
  private final FileMetricsSectorDao fmDao;
  private final boolean dryRun;

  private int examined;
  private int deletableFiles;
  private int deletedFiles;
  private int failedFiles;
  private boolean noTable;

  public SectorImportPrunedSweepJob(int userKey, SqlSessionFactory factory, FileMetricsSectorDao fmDao, boolean dryRun) {
    super(userKey, JobPriority.LOW);
    this.factory = factory;
    this.fmDao = fmDao;
    this.dryRun = dryRun;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  @Override
  public void execute() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      if (!session.getMapper(SectorImportPrunedMapper.class).tableExists()) {
        LOG.info("No sector_import_pruned table, so there is nothing to sweep. Already done, or the job migration never ran here.");
        noTable = true;
        return;
      }
      LOG.info("Sweeping the names files of {} pruned sector syncs{}",
        session.getMapper(SectorImportPrunedMapper.class).count(), dryRun ? " (DRY RUN)" : "");
    }

    PrunedAttempt cursor = null;
    while (true) {
      checkIfCancelled();
      final List<PrunedAttempt> page;
      // short autocommit read, closed again before any filesystem work - the files live on slow NFS
      try (SqlSession session = factory.openSession(true)) {
        page = session.getMapper(SectorImportPrunedMapper.class).list(cursor, PAGE);
      }
      if (page.isEmpty()) {
        break;
      }

      for (PrunedAttempt a : page) {
        checkIfCancelled();
        examined++;
        sweep(a);
        if (examined % 100_000 == 0) {
          LOG.info("Swept {} pruned syncs, {} files {}", examined, dryRun ? deletableFiles : deletedFiles, verb());
          setStep(progress());
        }
      }

      final PrunedAttempt last = page.get(page.size() - 1);
      if (dryRun) {
        // nothing is removed, so the cursor has to carry us forward
        cursor = last;
      } else {
        try (SqlSession session = factory.openSession(true)) {
          session.getMapper(SectorImportPrunedMapper.class).deleteUpTo(last);
        }
      }
      if (page.size() < PAGE) {
        break;
      }
    }

    LOG.info("Sweep {}: examined {}, files {} {}, failedFiles {}",
      dryRun ? "DRY RUN" : "done", examined, dryRun ? deletableFiles : deletedFiles, verb(), failedFiles);
  }

  /**
   * Set here rather than at the end of execute because a job that succeeded has its step cleared.
   */
  @Override
  protected void onFinish() {
    setStep(noTable ? "nothing to sweep" : progress());
  }

  /**
   * Deletes one names file, using the delete as its own existence check so a real run costs a single NFS
   * round trip. One unremovable file is logged and counted rather than aborting a run over millions.
   */
  private void sweep(PrunedAttempt a) {
    final DSID<Integer> key = DSID.of(a.getDatasetKey(), a.getSectorKey());
    if (dryRun) {
      if (fmDao.namesFile(key, a.getAttempt()).exists()) {
        deletableFiles++;
      }
    } else {
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

  private String verb() {
    return dryRun ? "deletable" : "deleted";
  }

  private String progress() {
    return (dryRun ? "DRY RUN: " : "") + "examined " + examined
           + ", " + (dryRun ? deletableFiles : deletedFiles) + " files " + verb()
           + (failedFiles > 0 ? ", " + failedFiles + " files failed" : "");
  }
}

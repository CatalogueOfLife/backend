package life.catalogue.release.extended;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A single sector merge used to built an extended release.
 * It shares some logic with the regular SectorSync, but it does not need all the wrapping, indexing and callback handling
 * as we do not want to run this as a standalone or even async job.
 */
public class SectorMerge {
  private static final Logger LOG = LoggerFactory.getLogger(SectorMerge.class);
  private final SqlSessionFactory factory;
  private final SectorImportDao sid;
  private final Sector sector;
  private final Map<String, EditorialDecision> decisions = new HashMap<>();
  private final int userKey;
  private final SectorImport state;
  private final int releaseKey;

  public SectorMerge(int releaseKey, Sector sector, int userKey, SqlSessionFactory factory, SectorImportDao sid) {
    this.releaseKey = releaseKey;
    this.factory = factory;
    this.sid = sid;
    this.sector = sector;
    this.userKey = userKey;
    state = new SectorImport();
    state.setSectorKey(sector.getId());
    state.setDatasetKey(sector.getDatasetKey());
    state.setJob(getClass().getSimpleName());
    state.setState(ImportState.WAITING);
  }

  public void run() throws Exception {
    MDC.put(LoggingUtils.MDC_KEY_SECTOR, String.valueOf(sector.getId()));
    LOG.info("Start {} for sector {}", this.getClass().getSimpleName(), sector);
    boolean failed = true;

    try {
      init();

      state.setState( ImportState.INSERTING);
      LOG.info("Merge data for sector {}", sector);
      merge();

      state.setState( ImportState.FINISHED);
      LOG.info("Completed {} for sector {}", this.getClass().getSimpleName(), sector);
      failed = false;

    } catch (InterruptedException e) {
      state.setState(ImportState.CANCELED);
      throw e;

    } catch (Exception e) {
      state.setError(ExceptionUtils.getRootCauseMessage(e));
      state.setState(ImportState.FAILED);
      // rethrow, we only catch to change the state for logs
      throw e;

    } finally {
      state.setFinished(LocalDateTime.now());
      // persist sector import
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(SectorImportMapper.class).update(state);
        // update sector with latest attempt on success
        if (!failed) {
          sector.setSyncAttempt(state.getAttempt()); // important as we share the instance with ExtendedRelease
          session.getMapper(SectorMapper.class).updateLastSync(sector, state.getAttempt());
        }
      }
      MDC.remove(LoggingUtils.MDC_KEY_SECTOR);
    }
  }

  private void init(){
    // create new sync metrics instance
    state.setState(ImportState.PREPARING);
    state.setStarted(LocalDateTime.now());
    state.setCreatedBy(userKey);
    // first create the sync metrics record so we have a new attempt!
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(SectorImportMapper.class).create(state);
      session.getMapper(DecisionMapper.class).processSearch(DecisionSearchRequest.byDataset(sector.getDatasetKey(), sector.getSubjectDatasetKey())).forEach(ed -> {
        decisions.put(ed.getSubject().getId(), ed);
      });
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sector);
  }

  private void merge() throws Exception {

  }
}

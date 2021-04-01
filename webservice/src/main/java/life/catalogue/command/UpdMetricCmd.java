package life.catalogue.command;

import com.google.common.base.Preconditions;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.util.PrimitiveUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Creates missing sector import metrics for all releases of a given project.
 * An optional update flag allows to also rebuilt existing metrics.
 * TODO: merge with MetricsUpdater admin job to also update all metrics if requested (datasets & sector)
 */
public class UpdMetricCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdMetricCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_UPDATE = "update";
  private Integer key;
  private boolean update;
  private SectorImportDao sid;

  public UpdMetricCmd() {
    super("updMetrics", "Update all release sector metrics for the given projects dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false)
      .help("Dataset key for project to update");
    subparser.addArgument("--"+ ARG_UPDATE)
      .dest(ARG_UPDATE)
      .type(Boolean.class)
      .required(false)
      .help("Flag to update also existing metrics");
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkArgument(user != null, "User argument required to run the updater");
    Preconditions.checkArgument(user.hasRole(User.Role.ADMIN), "Admin user required to run the updater");
    key = ns.getInt(ARG_KEY);
    Preconditions.checkArgument(key != null, "Single key parameter required to specify a project to update");
    update = PrimitiveUtils.eval(ns.getBoolean(ARG_UPDATE));

    var info = DatasetInfoCache.CACHE.info(key);
    info.requireOrigin(DatasetOrigin.MANAGED);

    // setup daos
    sid = new SectorImportDao(factory, cfg.metricsRepo);

    // retrieve all releases incl private ones if user rights allow
    List<Dataset> releases;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setReleasedFrom(key);
      releases = dm.search(req, userKey, new Page(0, 1000));
    }

    LOG.info("Updating sector metrics for {} releases of project {}", releases.size(), key);
    for (Dataset d : releases) {
      updateRelease(d);
    }
    LOG.info("Successfully updated sector metrics for {} releases of project {}", releases.size(), key);
  }

  private void updateRelease(final Dataset rel) {
    LOG.info("Updating sector metrics for release {}: {}", rel.getKey(), rel.getAliasOrTitle());
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger created = new AtomicInteger(0);
    final AtomicInteger updated = new AtomicInteger(0);
    try (SqlSession session = factory.openSession()) {
      final SectorMapper sm = session.getMapper(SectorMapper.class);
      final SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      sm.processDataset(rel.getKey()).forEach(s -> {
        counter.incrementAndGet();
        if (s.getSyncAttempt() == null) {
          LOG.warn("Ignore sector {} without last sync attempt from release {}", s.getId(), rel.getKey());

        } else {
          boolean calcMetrics = false;
          // get sector import in project for given attempt
          SectorImport si = sim.get(DSID.of(key, s.getId()), s.getSyncAttempt());
          if (si == null) {
            // missing, rebuilt it
            si = new SectorImport();
            si.setSectorKey(s.getId());
            si.setDatasetKey(key); // datasetKey must be the project, that's where we store all metrics !!!
            si.setAttempt(s.getSyncAttempt());
            si.setJob(getClass().getSimpleName());
            si.setState(null);
            si.setCreatedBy(user.getKey());
            // how can we approximately recreate with those timestamps ?
            si.setStarted(LocalDateTime.now());
            sim.create(si);
            created.incrementAndGet();
            calcMetrics = true;

          } else if (update) {
            updated.incrementAndGet();
            calcMetrics = true;
            if (si.getState() != ImportState.FINISHED) {
              LOG.warn("Sector import {} from release {} has state {}. {} metrics.", si.attempt(), rel.getKey(), si.getState(), update ? "Update":"Keep");
            }
          }

          if (calcMetrics) {
            sid.updateMetrics(si, rel.getKey());
            if (si.getState()==null) {
              si.setState(ImportState.FINISHED);
            }
            if (si.getFinished()==null) {
              si.setFinished(LocalDateTime.now());
            }
            sim.update(si);
          }
        }
      });
    }
    LOG.info("Created/updated {}/{} metrics from {} sectors in release {}: {}", created, updated, counter, rel.getKey(), rel.getAliasOrTitle());
  }
}

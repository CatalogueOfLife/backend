package life.catalogue.command;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
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

public class UpdReleaseMetricCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdReleaseMetricCmd.class);
  private static final String ARG_KEY = "key";
  private Integer key;
  private SectorImportDao sid;


  public UpdReleaseMetricCmd() {
    super("update-release-metrics", "Update all release sector metrics for the given projects dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .nargs("*")
      .type(Integer.class)
      .required(false)
      .help("Dataset key for project to update");
  }

  @Override
  public void execute() throws Exception {
    key = ns.getInt(ARG_KEY);
    if (key == null) {
      LOG.info("Single key parameter required to specify a project to update");
      throw new IllegalArgumentException("Single key parameter required to specify a project to update");
    }

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
          SectorImport si = sim.get(s, s.getSyncAttempt());
          if (si == null) {
            si = new SectorImport();
            si.setSectorKey(s.getId());
            si.setDatasetKey(key); // datasetKey must be the project, thats where we store all metrics !!!
            si.setAttempt(s.getSyncAttempt());
            si.setJob(getClass().getSimpleName());
            si.setState(ImportState.ANALYZING);
            si.setCreatedBy(user.getKey());
            // how can we approximately recreate with those timestamps ?
            si.setStarted(LocalDateTime.now());
            sim.create(si);
            created.incrementAndGet();
          } else {
            if (si.getState() != ImportState.FINISHED) {
              LOG.warn("Sector import {} from release {} has state {}. Update metrics anyways.", si.attempt(), rel.getKey(), si.getState());
            }
            updated.incrementAndGet();
          }

          sid.updateMetrics(si, rel.getKey());
          si.setState(ImportState.FINISHED);
          si.setFinished(LocalDateTime.now());
          sim.update(si);
        }
      });
    }
    LOG.info("Created/updated {}/{} metrics from {} sectors in release {}: {}", created, updated, counter, rel.getKey(), rel.getAliasOrTitle());
  }
}

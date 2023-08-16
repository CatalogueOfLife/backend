package life.catalogue.command;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Creates missing sector import metrics for all releases of a given project.
 * An optional update flag allows to also rebuilt existing metrics.
 */
public class UpdMetricCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdMetricCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_ALL = "all";
  private static final String ARG_UPDATE = "update";
  private Integer key;
  private boolean update;
  private SectorImportDao sid;
  private DatasetImportDao did;
  private final Set<SectorAttempt> done = new HashSet<>();

  public UpdMetricCmd() {
    super("updMetrics", false, "Update all release sector metrics for the given projects dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_ALL)
      .dest(ARG_ALL)
      .type(Boolean.class)
      .required(false)
      .setDefault(false)
      .help("Flag forcing an update of all metrics for all datasets");
    subparser.addArgument("--"+ ARG_UPDATE)
      .dest(ARG_UPDATE)
      .type(Boolean.class)
      .setDefault(false)
      .required(false)
      .help("Flag to update also existing metrics");
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false)
      .help("Dataset key for project to update");
  }

  static class SectorAttempt implements DSID<Integer> {
    public final int datasetKey;
    public final int sectorKey;
    public final int attempt;

    SectorAttempt(int datasetKey, int sectorKey, int attempt) {
      this.datasetKey = datasetKey;
      this.sectorKey = sectorKey;
      this.attempt = attempt;
    }

    @Override
    public Integer getId() {
      return sectorKey;
    }

    @Override
    public void setId(Integer id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Integer getDatasetKey() {
      return datasetKey;
    }

    @Override
    public void setDatasetKey(Integer key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SectorAttempt)) return false;
      SectorAttempt that = (SectorAttempt) o;
      return datasetKey == that.datasetKey &&
        sectorKey == that.sectorKey &&
        attempt == that.attempt;
    }

    @Override
    public int hashCode() {
      return Objects.hash(datasetKey, sectorKey, attempt);
    }
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkArgument(user != null, "User argument required to run the updater");
    Preconditions.checkArgument(user.hasRole(User.Role.ADMIN), "Admin user required to run the updater");

    // setup
    sid = new SectorImportDao(factory, cfg.metricsRepo);
    did = new DatasetImportDao(factory, cfg.metricsRepo);

    if (ns.getBoolean(ARG_ALL)) {
      update = true;
      updateAll();
    } else {
      update = ns.getBoolean(ARG_UPDATE);
      key = ns.getInt(ARG_KEY);
      Preconditions.checkArgument(key != null, "Single key parameter required to specify a project to update");
      updateProjectMetrics();
    }
  }

  private void updateAll() {
    LOG.info("Start metrics update for all datasets");
    final AtomicInteger counter = new AtomicInteger(0);
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      PgUtils.consume(
        () -> dm.process(null),
        d -> {
          counter.incrementAndGet();
          updateDataset(d);
        }
      );
    }
    LOG.info("Finished metrics update, updating {} datasets", counter);
  }

  private void updateDataset(Dataset d) {
    try {
      final boolean isRelease = d.getOrigin().isRelease();
      // the datasetKey to store metrics under - the project in case of a release
      int datasetKey = isRelease ? d.getSourceKey() : d.getKey();
      if (d.getOrigin() == DatasetOrigin.PROJECT || d.getAttempt() == null) {
        LOG.info("No import existing for dataset {}", d.getKey());

      } else {
        int attempt = d.getAttempt();
        DatasetImport di = did.getAttempt(datasetKey, attempt);
        if (di == null) {
          LOG.warn("No import metrics exist for dataset {} attempt {}, but which was given in dataset {}", datasetKey, attempt, d.getKey());
          //TODO: built new metrics !!!
        } else {
          LOG.info("Build import metrics for dataset " + d.getKey());
          did.updateMetrics(di, d.getKey());
          did.update(di);
        }
      }

      // SECTORS for managed & released datasets
      updateSectorMetrics(d);
    } catch (Exception e) {
      LOG.error("Failed to update metrics for dataset {}", d.getKey(), e);
    }
  }

  private void updateProjectMetrics() {
    var info = DatasetInfoCache.CACHE.info(key);
    info.requireOrigin(DatasetOrigin.PROJECT);

    // retrieve all releases incl private ones
    Dataset project;
    List<Dataset> releases;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      project = dm.get(key);
      releases = dm.listReleases(key);
      releases.removeIf(Dataset::hasDeletedDate);
    }

    // first update the latest version of the project itself - it might have newer syncs
    updateSectorMetrics(project);

    LOG.info("Updating sector metrics for {} releases of project {}", releases.size(), key);
    for (Dataset d : releases) {
      updateSectorMetrics(d);
    }

    LOG.info("Successfully updated sector metrics for {} releases of project {}", releases.size(), key);
  }

  private void updateSectorMetrics(final Dataset d) {
    if (!d.getOrigin().isManagedOrRelease()) {
      LOG.debug("Dataset {} is not a project or release but {}", d.getKey(), d.getOrigin());
      return;
    }

    final int projectKey;
    final LocalDateTime createdTime;
    if (d.getOrigin().isRelease()) {
      projectKey = d.getSourceKey();
      createdTime = d.getCreated();
      LOG.info("Updating sector metrics for project {} {} {}#{}", projectKey, d.getOrigin(), d.getKey(), d.getAttempt());

    } else {
      projectKey = d.getKey();
      createdTime = LocalDateTime.now();
      LOG.info("Updating sector metrics for project {}: {}", d.getKey(), d.getAliasOrTitle());
    }
    final String kind = d.getOrigin().name().toLowerCase();

    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger created = new AtomicInteger(0);
    final AtomicInteger updated = new AtomicInteger(0);
    final AtomicInteger doneBefore = new AtomicInteger(0);
    try (SqlSession session = factory.openSession(true)) {
      final SectorMapper sm = session.getMapper(SectorMapper.class);
      final SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      final DatasetImportMapper dim = session.getMapper(DatasetImportMapper.class);

      PgUtils.consume(()->sm.processDataset(d.getKey()), s -> {
        counter.incrementAndGet();
        if (s.getSyncAttempt() == null) {
          LOG.info("Ignore sector {} without last sync attempt from {} {}", s.getId(), kind, d.getKey());

        } else {
          SectorAttempt sa = new SectorAttempt(projectKey, s.getId(), s.getSyncAttempt());
          if (done.contains(sa)) {
            doneBefore.incrementAndGet();

          } else {
            done.add(sa);
            boolean calcMetrics = false;
            // get sector import in project for given attempt
            SectorImport si = sim.get(sa, sa.attempt);
            if (si == null) {
              // missing, rebuilt it
              si = new SectorImport();
              si.setSectorKey(s.getId());
              si.setDatasetKey(projectKey); // datasetKey must be the project, that's where we store all metrics !!!
              si.setAttempt(sa.attempt);
              si.setJob(getClass().getSimpleName());
              si.setState(ImportState.ANALYZING);
              si.setCreatedBy(user.getKey());
              // we approximate the time of the sync:
              // it has to be after the sector was created and the source dataset imported
              // but before the release was done or the next dataset import happened
              LocalDateTime min = s.getCreated();
              LocalDateTime max = createdTime;
              if (s.getDatasetAttempt() != null) {
                var di = dim.get(s.getSubjectDatasetKey(), s.getDatasetAttempt());
                if (di != null) {
                  if (di.getFinished().isAfter(min)) {
                    min = di.getFinished();
                  }
                  var di2 = dim.getNext(s.getSubjectDatasetKey(), s.getDatasetAttempt(), ImportState.FINISHED);
                  if (di2 != null) {
                    if (di2.getFinished().isBefore(max)) {
                      max = di2.getFinished();
                    }
                  }
                }
              }

              var diffTime = ChronoUnit.MINUTES.between(min, max);
              LocalDateTime syncTime = min.plus(diffTime / 2, ChronoUnit.MINUTES);

              si.setStarted(syncTime);
              sim.create(si);
              created.incrementAndGet();
              calcMetrics = true;

            } else if (update) {
              updated.incrementAndGet();
              calcMetrics = true;
              if (si.getState() != ImportState.FINISHED) {
                LOG.warn("Sector import {} from {} {} has state {}. {} metrics.", si.attempt(), kind, d.getKey(), si.getState(), update ? "Update":"Keep");
              }
            }

            if (calcMetrics) {
              sid.updateMetrics(si, d.getKey());
              if (si.getState()==ImportState.ANALYZING) {
                si.setState(ImportState.FINISHED);
              }
              if (si.getFinished()==null) {
                si.setFinished(si.getStarted().plus(1, ChronoUnit.MINUTES));
              }
              sim.update(si);
            }
          }
        }
      });
    }
    LOG.info("Created/updated {}/{} metrics from {} sectors in {} {}#{}. {} sectors done before", created, updated, counter, kind, d.getKey(), d.getAttempt(), doneBefore);
  }
}

package life.catalogue.command;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.collection.CountMap;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.UserDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.service.*;
import life.catalogue.event.EventBroker;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.collections4.iterators.PeekingIterator;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import net.sourceforge.argparse4j.inf.Subparser;

import static life.catalogue.api.vocab.DatasetOrigin.PROJECT;

/**
 * Tool for managing / updating DOIs for projects releases and dataset version DOIs.
 */
public class DoiUpdateCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdateCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_DOI = "doi";
  private static final String ARG_ALL = "all";
  private static final String ARG_VERSIONS = "versions";
  private static final String ARG_THREADS = "threads";
  private DoiService doiService;
  private DatasetConverter converter;
  private Integer versions  ;
  private CountMap<DatasetOrigin> created = new CountMap<>();
  private CountMap<DatasetOrigin> updated = new CountMap<>();
  private CountMap<DatasetOrigin> published = new CountMap<>();
  private ExecutorService executor;

  public DoiUpdateCmd() {
    super("doi", true, "Update all project, release and release source DOIs for the given project dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false)
      .help("Dataset key for project and all it's release or a single dataset to update");
    subparser.addArgument("--"+ ARG_DOI)
      .dest(ARG_DOI)
      .type(DOI.class)
      .required(false)
      .help("Specific DOI to update");
    subparser.addArgument("--"+ ARG_ALL)
      .dest(ARG_ALL)
      .type(Boolean.class)
      .required(false)
      .help("Update all DOIs for all releases and external datasets, including their ");
    subparser.addArgument("--"+ ARG_VERSIONS)
      .dest(ARG_VERSIONS)
      .type(Integer.class)
      .required(false)
      .help("Number of previous versions to update. If -1 all successful import attempts will be assigned a version DOI");
    subparser.addArgument("--"+ ARG_THREADS, "-t")
      .dest(ARG_THREADS)
      .type(Integer.class)
      .setDefault(1)
      .required(false)
      .help("Number of threads to talk to DataCite in parallel");
  }

  @Override
  public void execute() throws Exception {
    try {
      Preconditions.checkNotNull(cfg.doi, "DOI configs needed to run the updater");
      Preconditions.checkArgument(user != null, "User argument required to run the updater");
      Preconditions.checkArgument(user.isAdmin(), "Admin user required to run the updater");

      // setup
      doiService = new DataCiteService(cfg.doi, jerseyClient);
      Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      UserDao udao = new UserDao(factory, cfg.mail, null, new EventBroker(cfg.broker), validator);
      converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
      var threads = ns.getInt(ARG_THREADS);
      this.executor = Executors.newFixedThreadPool(threads, new NamedThreadFactory("datacite-worker"));
      versions = ns.getInt(ARG_VERSIONS);

      var doiStr = ns.getString(ARG_DOI);
      var key = ns.getInt(ARG_KEY);

      if (doiStr != null) {
        var doi = new DOI(doiStr);
        updateDOI(doi);
      } else if (key != null) {
        updateDataset(key);
      } else {
        var all = ns.getBoolean(ARG_ALL);
        if (Boolean.TRUE.equals(all)) {
          updateAll();
        }
      }
    } finally {
      if (executor != null) {
        ExecutorUtils.shutdown(executor);
      }
    }
    LOG.info("Done");
    LOG.info("Created {}, updated {} and published {} DOIs", created.total(), updated.total(), published.total());
  }

  private void updateDOI(DOI doi) throws DoiException {
    if (!doi.isCOL()) {
      throw new IllegalArgumentException("DOI needs to be a Catalogue of Life DOI");
    }
    if (doi.isDatasetVersion()) {
      throw new IllegalArgumentException("Dataset version DOIs cannot be updated at this stage. Instead update the dataset");
    } else {
      updateDataset(doi.datasetKey());
    }
  }

  private void updateAll() throws Exception {
    List<Integer> keys;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.keys(false, DatasetOrigin.PROJECT);
    }
    LOG.info("Update DOIs for {} projects and their releases", keys.size());
    for (var key : keys) {
      updateProject(key);
    }

    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.keys(false, DatasetOrigin.EXTERNAL);
    }
    LOG.info("Update DOIs for {} external datasets", keys.size());
    for (var key : keys) {
      updateDataset(key);
    }
  }

  private void updateDataset(int datasetKey) {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.PROJECT) {
      updateProject(datasetKey);
    } else if (info.origin == DatasetOrigin.RELEASE || info.origin == DatasetOrigin.XRELEASE) {
      // update single release
      throw new UnsupportedOperationException("Update of single release DOIs not yet supported");
    } else {
      // regular dataset
      updateExternalDataset(info.key);
    }
  }

  private void updateExternalDataset(int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var d = dm.get(datasetKey);
      var create = assertDoiExists(d);
      LOG.info("{} DOI {}for external dataset {}: {}", create?"Create":"Update", versions==null?"":" and it's versions ", datasetKey, d.getTitle());
      executor.execute(new DataciteSync(d.getDoi(), converter.dataset(d), d.getKey(), create));
      // also update past import versions in the archive?
      if (versions != null) {
        DOI next = null;
        // first update the current version of the dataset itself
        boolean createV = false;
        if (d.getVersionDoi() == null) {
          createV = true;
          d.setVersionDoi(cfg.doi.datasetVersionDOI(d.getKey(), d.getAttempt()));
          dm.updateVersionDOI(d.getKey(), d.getVersionDoi());
        }
        // we wait with updating the DOI metadata until we know the last version DOI
        DOI firstArchivedVersionDOI = null;
        // now the archived versions
        int countDown = versions;
        var dam = session.getMapper(DatasetArchiveMapper.class);
        // we retrieve past version with the most current attempt first in the imports list
        var imports = PgUtils.toList(dam.processDataset(d.getKey()));
        var iter = PeekingIterator.peekingIterator(imports.iterator());
        while (iter.hasNext() && countDown != 0) {
          var v = iter.next();
          countDown--;
          // add missing version DOI
          if (v.getVersionDoi() == null) {
            createV = true;
            v.setVersionDoi(cfg.doi.datasetVersionDOI(v.getKey(), v.getAttempt()));
            dam.updateVersionDOI(v.getKey(), v.getAttempt(), v.getVersionDoi());
          } else {
            createV = false;
          }
          // remember the first archived version DOI for the current dataset update at the end
          if (firstArchivedVersionDOI == null) {
            firstArchivedVersionDOI=v.getVersionDoi();
          }
          var prev = iter.peek();
          DOI prevDOI = prev != null ? prev.getVersionDoi() : null;
          executor.execute(new DataciteSync(v.getVersionDoi(), converter.datasetVersion(v, prevDOI, next), v.getKey(), createV));
          next = v.getVersionDoi();
        }
        // now we also update the current version
        executor.execute(new DataciteSync(d.getVersionDoi(), converter.datasetVersion(d, firstArchivedVersionDOI, next), d.getKey(), createV));
      }
    }
  }

  /**
   * projects do not have a DOI, but all their releases do.
   * @param projectKey
   * @throws RuntimeException
   */
  private void updateProject(Integer projectKey) throws RuntimeException {
    Dataset project;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      project = dm.get(projectKey);
      if (project == null) {
        throw NotFoundException.notFound(Dataset.class, projectKey);
      } else if (project.getOrigin() != PROJECT) {
        throw new IllegalArgumentException("Dataset " + projectKey + " is not a project but a " + project.getOrigin() + " dataset");
      }
    }
    // update releases, keeping the origins separate
    updateReleases(project, DatasetOrigin.RELEASE);
    updateReleases(project, DatasetOrigin.XRELEASE);
  }

  private void updateReleases(Dataset project, DatasetOrigin origin) {
    LOG.info("Update DOIs for all {} of project {}: {}", origin, project.getKey(), project.getTitle());
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      final var latestReleaseKey = dm.latestRelease(project.getKey(), true, origin);
      LOG.info("Latest {} of project {} is {}", origin, project.getKey(), latestReleaseKey);
      // list all releases in chronological order, starting with the very first release
      var iter = PeekingIterator.peekingIterator(dm.listReleases(project.getKey()).iterator());
      DOI prev = null;
      while (iter.hasNext()) {
        var release = iter.next();
        // ignore private releases, only public ones have a DOI
        if (release.isPrivat() || release.getOrigin() != origin) continue;

        boolean create = assertDoiExists(release);
        var next = iter.peek();
        var nextDOI = next != null ? cfg.doi.datasetDOI(next.getKey()) : null;
        final boolean portal = Objects.equals(latestReleaseKey, release.getKey());
        executor.execute(new DataciteSync(release.getDoi(), converter.release(release, portal, prev, nextDOI), release.getKey(), create));
        prev = release.getDoi();
      }
    }
  }

  private boolean assertDoiExists(Dataset d) {
    if (d.getDoi() == null) {
      d.setDoi(cfg.doi.datasetDOI(d.getKey()));
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        dm.updateDOI(d.getKey(), d.getDoi());
      }
      return true;
    }
    return false;
  }

  class DataciteSync implements Runnable {
    private final DOI doi;
    private final boolean create;
    private final DoiAttributes metadata;
    private final DatasetInfoCache.DatasetInfo info;

    DataciteSync(DOI doi, DoiAttributes metadata, int datasetKey, boolean create) {
      this.doi = doi;
      this.create = create;
      this.metadata = metadata;
      this.info = DatasetInfoCache.CACHE.info(datasetKey);
    }

    @Override
    public void run() {
      try {
        metadata.setDoi(doi);
        if (create) {
          LOG.info("Create DOI {} for {} {}", doi, info.origin, info.key);
          doiService.create(metadata);
          created.inc(info.origin);
        } else {
          LOG.info("Update DOI {} for {} {}", doi, info.origin, info.key);
          doiService.update(metadata);
          updated.inc(info.origin);
        }
        var data = doiService.resolve(doi);
        if (data.getState() != DoiState.FINDABLE) {
          LOG.info("Publish DOI {} for {} {}", doi, info.origin, info.key);
          doiService.publish(doi);
          published.inc(info.origin);
        } else {
          LOG.info("Final state of DOI {}: {}", doi, data.getState());
        }
      } catch (DoiException e) {
        LOG.error("Failed to sync DOI {} for {} {} with Datacite", doi, info.origin, info.key, e);
      }
    }
  }
}

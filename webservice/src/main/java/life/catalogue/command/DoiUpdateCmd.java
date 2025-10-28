package life.catalogue.command;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.UserDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.service.DataCiteService;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;
import life.catalogue.event.EventBroker;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import net.sourceforge.argparse4j.inf.Subparser;

import static life.catalogue.api.vocab.DatasetOrigin.PROJECT;
import static life.catalogue.api.vocab.DatasetOrigin.XRELEASE;

/**
 * Tool for managing / updating DOIs for projects and it's releases.
 */
public class DoiUpdateCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdateCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_DOI = "doi";
  private DoiService doiService;
  private DatasetConverter converter;
  private int releaseUpdated = 0;
  private int releaseCreated = 0;
  private int releasePublished = 0;
  private int sourceUpdated = 0;
  private int sourcePublished = 0;

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
      .help("Dataset key for project to update");
    subparser.addArgument("--"+ ARG_DOI)
      .dest(ARG_DOI)
      .type(DOI.class)
      .required(false)
      .help("Dataset release DOI to update");
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkNotNull(cfg.doi, "DOI configs needed to run the updater");
    Preconditions.checkArgument(user != null, "User argument required to run the updater");
    Preconditions.checkArgument(user.isAdmin(), "Admin user required to run the updater");

    // setup
    doiService = new DataCiteService(cfg.doi, jerseyClient);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    UserDao udao = new UserDao(factory, cfg.mail, null, new EventBroker(cfg.broker), validator);
    converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);

    var doiStr = ns.getString(ARG_DOI);
    if (doiStr != null) {
      var doi = new DOI(doiStr);
      updateDOI(doi);
    } else {
      var key = ns.getInt(ARG_KEY);
      if (key == null) {
        throw new IllegalArgumentException("Either project key or doi needed to be specified");
      }
      updateProject(key);
    }
    LOG.info("Done");
  }

  private void updateProject(Integer projectKey) throws Exception {
    Dataset project;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      project = dm.get(projectKey);
      if (project == null) {
        throw NotFoundException.notFound(Dataset.class, projectKey);
      } else if (project.getOrigin() != PROJECT) {
        throw new IllegalArgumentException("Dataset " + projectKey + " is not a project but a " + project.getOrigin() + " dataset");
      }
      LOG.info("Update DOI {} for project {}: {}", project.getDoi(), projectKey, project.getTitle());
      updateReleaseOrProject(project, false, null, null, dm);
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
      DOI prev = null;
      for (Dataset release : dm.listReleases(project.getKey())) {
        // ignore private releases, only public ones have a DOI
        if (release.isPrivat() || release.getOrigin() != origin) continue;

        final boolean portal = Objects.equals(latestReleaseKey, release.getKey()) && origin != XRELEASE; // point XR always at CLB for now !!!
        updateReleaseOrProject(release, portal, project.getDoi(), prev, dm);
        if (release.getDoi() != null) {
          updateReleaseSources(release, portal);
          prev = release.getDoi();
        }
      }
    }
  }

  private void updateDOI(DOI doi) throws DoiException {
    if (!doi.isCOL()) {
      throw new IllegalArgumentException("DOI needs to be a Catalogue of Life DOI");
    }

    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset release = dm.getByDoi(doi);
      if (release == null) {
        throw NotFoundException.notFound(Dataset.class, doi);
      } else if (!release.getOrigin().isRelease()) {
        throw new IllegalArgumentException("Dataset " + doi + " is not a release but a " + release.getOrigin() + " dataset");
      }
      Dataset project = dm.get(release.getSourceKey());
      Dataset prev = dm.getPreviousRelease(release.getKey());
      Dataset next = dm.getNextRelease(release.getKey());
      updateDOI(doi, release, next==null, project.getDoi(), prev==null ? null : prev.getDoi());
    }
  }
  private void updateReleaseOrProject(Dataset release, boolean portal, @Nullable DOI project, @Nullable DOI prev, DatasetMapper dm) {
    DOI doi = release.getDoi();
    try {
      if (doi == null) {
        // issue a new DOI!
        doi = doiService.fromDataset(release.getKey());
        release.setDoi(doi);
        dm.update(release); // persist doi
        var attr = converter.release(release, portal, project, prev);
        LOG.info("Issue new DOI {} for release {}", doi, release.getKey());
        try {
          doiService.create(attr);
          releaseCreated++;
          doiService.publish(doi);
          releasePublished++;
        } catch (DoiException e) {
          LOG.info("Failed to create DOI {} for release {}. Try to do an update instead", doi, release.getKey(), e);
          updateDOI(doi, release, portal, project, prev);
        }

      } else {
        updateDOI(doi, release, portal, project, prev);
      }
    } catch (DoiException e) {
      LOG.error("Error updating DOIs for release {} with DOI {}", release.getKey(), doi, e);
    } finally {
      LOG.info("Total releases created={}, updated={}, published={}. Total sources updated={}, published={}", releaseCreated, releaseUpdated, releasePublished,  sourceUpdated, sourcePublished);
    }
  }

  private void updateDOI(DOI doi, Dataset release, boolean isLatest, @Nullable DOI project, @Nullable DOI prev) throws DoiException {
    var data = doiService.resolve(doi);
    var attr = converter.release(release, isLatest, project, prev);
    LOG.info("Update DOI {} for release {}", doi, release.getKey());
    doiService.update(attr);
    releaseUpdated++;
    if (!release.isPrivat() && data.getState() != DoiState.FINDABLE) {
      doiService.publish(doi);
      releasePublished++;
    }
  }

  private void updateReleaseSources(Dataset release, boolean isLatest) {
    // we don't assign DOIs to XRelease sources at this stage
    if (release.getOrigin() == XRELEASE) {
      return;
    }

    try (SqlSession session = factory.openSession()) {
      LOG.info("Updating DOIs for {}release {} {}", isLatest ? "latest " : "", release.getKey(), release.getAlias());
      var dsm = session.getMapper(DatasetSourceMapper.class);
      for (Dataset source : dsm.listReleaseSources(release.getKey(), false)) {
        final DOI srcDoi = source.getDoi();
        if (srcDoi != null && srcDoi.isCOL()) {
          try {
            var data = doiService.resolve(srcDoi);
            var attr = converter.source(source, null, release, isLatest);
            LOG.info("Update DOI {} for source {} {}", srcDoi, source.getKey(), source.getAlias());
            doiService.update(attr);
            sourceUpdated++;
            if (!release.isPrivat() && data.getState() != DoiState.FINDABLE) {
              LOG.info("Publish DOI {} for source {} {} of public release {}", srcDoi, source.getKey(), source.getAlias(), release.getKey());
              doiService.publish(srcDoi);
              sourcePublished++;
            }

          } catch (DoiException e) {
            LOG.error("Error updating DOI {} for source {} in release {}", srcDoi, source.getKey(), release.getKey(), e);
          }
        }
      }
    }
  }
}

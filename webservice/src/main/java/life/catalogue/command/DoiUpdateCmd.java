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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

import net.sourceforge.argparse4j.inf.Subparser;

import static life.catalogue.api.vocab.DatasetOrigin.PROJECT;

/**
 * Tool for managing / updating DOIs for projects and it's releases.
 */
public class DoiUpdateCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdateCmd.class);
  private static final String ARG_KEY = "key";
  private DoiService doiService;
  private DatasetConverter converter;
  private int key;
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
      .required(true)
      .help("Dataset key for project to update");
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkNotNull(cfg.doi, "DOI configs needed to run the updater");
    Preconditions.checkArgument(user != null, "User argument required to run the updater");
    Preconditions.checkArgument(user.isAdmin(), "Admin user required to run the updater");

    // setup
    doiService = new DataCiteService(cfg.doi, jerseyClient);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    UserDao udao = new UserDao(factory, new EventBus(), validator);
    converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);

    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      key = ns.getInt(ARG_KEY);
      Dataset d = dm.get(key);
      if (d == null) {
        throw NotFoundException.notFound(Dataset.class, key);
      } else if (d.getOrigin() != PROJECT) {
        throw new IllegalArgumentException("Dataset "+key+" is not a project but a "+d.getOrigin()+" dataset");
      }
      // update project DOI
      Dataset project = dm.get(key);
      LOG.info("Update all DOIs for releases of project {}: {}", key, project.getTitle());
      //TODO: what about extended releases?
      updateReleaseOrProject(project, false, null, null, dm);
      final var latestReleaseKey = dm.latestRelease(d.getKey(), true, DatasetOrigin.RELEASE);
      LOG.info("Latest release of project {} is {}", key, latestReleaseKey);
      // list all releases in chronological order, starting with the very first release
      DOI prev = null;
      for (Dataset release : dm.listReleases(key)) {
        // ignore private releases, only public ones have a DOI
        if (release.isPrivat()) continue;

        final boolean isLatest = Objects.equals(latestReleaseKey, release.getKey());
        updateReleaseOrProject(release, isLatest, project.getDoi(), prev, dm);
        if (release.getDoi() != null) {
          updateReleaseSources(release, isLatest);
          prev = release.getDoi();
        }
      }
    }
  }

  private void updateReleaseOrProject(Dataset release, boolean isLatest, @Nullable DOI project, @Nullable DOI prev, DatasetMapper dm) {
    DOI doi = release.getDoi();
    try {
      if (doi == null) {
        // issue a new DOI!
        doi = doiService.fromDataset(release.getKey());
        release.setDoi(doi);
        dm.update(release); // persist doi
        var attr = converter.release(release, isLatest, project, prev);
        LOG.info("Issue new DOI {} for release {}", doi, release.getKey());
        try {
          doiService.create(attr);
          releaseCreated++;
          doiService.publish(doi);
          releasePublished++;
        } catch (DoiException e) {
          LOG.info("Failed to create DOI {} for release {}. Try to do an update instead", doi, release.getKey(), e);
          updateDOI(doi, release, isLatest, project, prev);
        }

      } else {
        updateDOI(doi, release, isLatest, project, prev);
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
    try (SqlSession session = factory.openSession()) {
      LOG.info("Updating DOIs for {}release {} {}", isLatest ? "latest " : "", release.getKey(), release.getAlias());
      var dsm = session.getMapper(DatasetSourceMapper.class);
      for (Dataset source : dsm.listReleaseSources(release.getKey())) {
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

package life.catalogue.command;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.eventbus.EventBus;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.dao.UserDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.service.*;

import org.apache.ibatis.session.SqlSession;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.sourceforge.argparse4j.inf.Subparser;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import static life.catalogue.api.vocab.DatasetOrigin.*;

/**
 * Tool for managing / updating COL DOIs.
 */
public class DoiUpdateCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdateCmd.class);
  private static final String ARG_KEY = "key";
  private DoiService doiService;
  private DatasetConverter converter;
  private int key;

  public DoiUpdateCmd() {
    super("updDoi", true, "Update all release and release source DOIs for the given release or project dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(true)
      .help("Dataset key for project or release to update");
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkArgument(user != null, "User argument required to run the updater");
    Preconditions.checkArgument(user.hasRole(User.Role.ADMIN), "Admin user required to run the updater");

    // setup
    doiService = new DataCiteService(cfg.doi, jerseyClient);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    UserDao udao = new UserDao(factory, new EventBus(), validator);
    converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);

    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      key = ns.getInt(ARG_KEY);
      Dataset d = dm.get(key);
      if (d == null) {
        throw NotFoundException.notFound(Dataset.class, key);

      } else if (d.getOrigin() == RELEASED) {
        var latestRelease = dm.latestRelease(d.getSourceKey(), true);
        updateRelease(d, latestRelease);

      } else if (d.getOrigin() == MANAGED) {
        var latestRelease = dm.latestRelease(d.getKey(), true);
        var req = new DatasetSearchRequest();
        for (Dataset release : dm.search(req, userKey, new Page(1000))) {
          updateRelease(release, latestRelease);
        }

      } else {
        throw new IllegalArgumentException("Datset "+key+" is an "+d.getOrigin()+" dataset");
      }
    }
  }

  private void updateRelease(Dataset release, Integer latestRelease) {
    try (SqlSession session = factory.openSession()) {
      if (release.getDoi() != null && release.getDoi().isCOL()) {
        final boolean isLatest = Objects.equals(latestRelease, release.getKey());

        final DOI doi = release.getDoi();
        try {
          var data = doiService.resolve(doi);
          var attr = converter.release(release, isLatest, null, isLatest);
          LOG.info("Update DOI {} for source {} {}", doi, source.getKey(), source.getAlias());
          doiService.update(attr);
          updated.incrementAndGet();
          if (!release.isPrivat() && data.getState() != DoiState.FINDABLE) {
            LOG.info("Publish DOI {} for source {} {} of public release {}", doi, source.getKey(), source.getAlias(), release.getKey());
            doiService.publish(doi);
          }

        } catch (DoiException e) {
          LOG.error("Error updating DOI {} for source {} in release {}", doi, source.getKey(), release.getKey(), e);
        }

        LOG.info("Updating DOIs for {}release {} {}", isLatest ? "latest " : "", release.getKey(), release.getAlias());
        AtomicInteger updated = new AtomicInteger(0);
        var dsm = session.getMapper(DatasetSourceMapper.class);
        for (Dataset source : dsm.listReleaseSources(release.getKey())) {
          final DOI doi = source.getDoi();
          if (doi != null && doi.isCOL()) {
            try {
              var data = doiService.resolve(doi);
              var attr = converter.source(source, null, release, isLatest);
              LOG.info("Update DOI {} for source {} {}", doi, source.getKey(), source.getAlias());
              doiService.update(attr);
              updated.incrementAndGet();
              if (!release.isPrivat() && data.getState() != DoiState.FINDABLE) {
                LOG.info("Publish DOI {} for source {} {} of public release {}", doi, source.getKey(), source.getAlias(), release.getKey());
                doiService.publish(doi);
              }

            } catch (DoiException e) {
              LOG.error("Error updating DOI {} for source {} in release {}", doi, source.getKey(), release.getKey(), e);
            }
          }
        }

      } else {
        LOG.info("Release {} {} has no DOI", release.getKey(), release.getAlias());
      }
    }
  }
}

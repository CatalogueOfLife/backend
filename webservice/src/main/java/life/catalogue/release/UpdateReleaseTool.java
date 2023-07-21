package life.catalogue.release;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dao.*;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.service.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.RematcherBase;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.eventbus.EventBus;
import com.zaxxer.hikari.HikariDataSource;

public class UpdateReleaseTool implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UpdateReleaseTool.class);

  static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  final SqlSessionFactory factory;
  final HikariDataSource dataSource;
  final Dataset release;
  final Dataset project;
  final DatasetSettings settings;
  final int userKey;
  final DoiConfig doiCfg;
  final DoiService doiService;
  final DoiUpdater doiUpdater;
  final DatasetSourceDao srcDao;

  public UpdateReleaseTool(int releaseKey, PgConfig pgCfg, DoiConfig doiCfg, int userKey) {
    dataSource = pgCfg.pool();
    this.userKey = userKey;
    factory = MybatisFactory.configure(dataSource, "tools");
    DatasetInfoCache.CACHE.setFactory(factory);
    srcDao = new DatasetSourceDao(factory);

    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      release = dm.get(releaseKey);
      if (!release.getOrigin().isRelease()) {
        throw new IllegalArgumentException("Dataset key "+releaseKey+" is not a release!");
      }
      project = dm.get(release.getSourceKey());
      settings = dm.getSettings(project.getKey());
    }
    System.out.printf("Loaded release %s: %s\n", release.getKey(), release.getTitle());

    // DOI
    this.doiCfg = doiCfg;
    doiService = buildDoiService(doiCfg);
    DatasetConverter converter = buildConverter(factory);
    LatestDatasetKeyCacheImpl cache = new LatestDatasetKeyCacheImpl(factory);
    doiUpdater = new DoiUpdater(factory, doiService, cache, converter);
  }

  static DatasetConverter buildConverter(SqlSessionFactory factory){
    UserDao udao = new UserDao(factory, new EventBus(), validator);
    return new DatasetConverter(
      URI.create("https://www.catalogueoflife.org"),
      URI.create("https://www.checklistbank.org"),
      udao::get
    );
  }
  static DataCiteService buildDoiService(DoiConfig cfg){
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig jerseyCfg = new ClientConfig(jacksonJsonProvider);
    jerseyCfg.register(new LoggingFeature(java.util.logging.Logger.getLogger(UpdateReleaseTool.class.getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    jerseyCfg.register(new UserAgentFilter());
    final Client client = ClientBuilder.newClient(jerseyCfg);
    return new DataCiteService(cfg, client);
  }

  /**
   * Rematches all sector targets for releases
   */
  public void rematchSectorTargets(){
    System.out.printf("Matching all sector targets of %s: %s\n\n", release.getKey(), release.getTitle());

    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    EstimateDao edao = new EstimateDao(factory, validator);
    NameDao ndao = new NameDao(factory, indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tdao = new TaxonDao(factory, ndao, indexService, validator);
    SectorDao sdao = new SectorDao(factory, indexService, tdao, validator);

    SectorRematchRequest req = new SectorRematchRequest();
    req.setAllowImmutableDatasets(true);
    req.setDatasetKey(release.getKey());
    //req.setId(1134);
    req.setTarget(true);
    req.setSubject(false);

    RematcherBase.MatchCounter mc = SectorRematcher.match(sdao, req, userKey);
    System.out.println("Sectors: " + mc);

    RematcherBase.MatchCounter mc2 = EstimateRematcher.match(edao, req, userKey);
    System.out.println("Estimates: " + mc2);
  }

  /**
   * Updates the metadata for all source which are based on an older attempt and archived version
   * from the most current, global dataset metadata and persists it in the source archive.
   */
  public void updateNotCurrentSourceMetadataFromLatest(){
    System.out.printf("Update all non current source metadata for %s: %s\n\n", release.getKey(), release.getTitle());
    AtomicInteger counter = new AtomicInteger(0);
    srcDao.list(release.getKey(), release, false).forEach(d -> {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        var dsm = session.getMapper(DatasetSourceMapper.class);
        var cm = session.getMapper(CitationMapper.class);
        var global = dm.get(d.getKey());
        if (global.getAttempt() > d.getAttempt()) {
          System.out.printf("Update source %s %s from attempt %s to %s:\n", d.getKey(), d.getAlias(), d.getAttempt(), global.getAttempt());
          System.out.println(global.getCitationText());
          dsm.delete(d.getKey(), release.getKey());
          dsm.create(release.getKey(), global);
          session.commit();
          counter.incrementAndGet();
        }
      }
    });
    System.out.printf("Updated %s sources for release %s\n\n", counter, release.getKey());
  }

  /**
   * Updates DOIs for all sources and makes them public if not already.
   */
  public void updateSourceDOIs(){
    System.out.printf("Publish all source DOIs for %s: %s\n\n", release.getKey(), release.getTitle());
    AtomicInteger updated = new AtomicInteger(0);
    AtomicInteger published = new AtomicInteger(0);
    srcDao.list(release.getKey(), release, false).forEach(d -> {
      if (d.getDoi() != null) {
        final DOI doi = d.getDoi();
        try {
          var data = doiService.resolve(doi);
          var srcAttr = doiUpdater.buildSourceMetadata(d, release, true);
          System.out.printf("Update DOI %s for source %s %s\n", doi, d.getKey(), d.getAlias());
          doiService.update(srcAttr);
          updated.incrementAndGet();
          if (data.getState() != DoiState.FINDABLE) {
            System.out.printf("Publish DOI %s for source %s %s\n", doi, d.getKey(), d.getAlias());
            doiService.publish(doi);
            published.incrementAndGet();
          }

        } catch (DoiException e) {
          System.err.printf("Error updating DOI %s for source %s\n", doi, d.getKey());
          throw new RuntimeException(e);
        }
      }
    });
    System.out.printf("\nUpdated %s DOIs for release %s\n", updated, release.getKey());
    System.out.printf("Published %s DOIs for release %s\n\n", published, release.getKey());
  }

  /**
   * Updates DOIs for all sources and makes them public if not already.
   */
  public void addMissingSourceDOIs(){
    System.out.printf("Issue missing source DOIs for %s: %s\n\n", release.getKey(), release.getTitle());
    AtomicInteger created = new AtomicInteger(0);
    final boolean publish = !release.isPrivat();
    try (SqlSession session = factory.openSession(true)) {
      DatasetSourceMapper dsm = session.getMapper(DatasetSourceMapper.class);
        srcDao.list(release.getKey(), release, false).forEach(d -> {
        if (d.getDoi() == null) {
          final DOI doi = doiCfg.datasetSourceDOI(release.getKey(), d.getKey());
          System.out.printf("Creating new DOI %s for source %s %s\n", doi, d.getKey(), d.getCitation());
          // update source
          d.setDoi(doi);
          dsm.delete(d.getKey(), release.getKey());
          dsm.create(release.getKey(), d);
          try {
            // make sure we done have any draft DOI still
            if (!doiService.delete(doi)) {
              System.err.printf("Failed to remove DOI %s for source %s\n", doi, d.getKey());
            }
          } catch (DoiException e) {
            System.err.printf("Error removing DOI %s for source %s\n", doi, d.getKey());
            e.printStackTrace();
          }
          var srcAttr = doiUpdater.buildSourceMetadata(d, release, true);
          doiService.createSilently(srcAttr);
          created.incrementAndGet();
          if (publish) {
            try {
              doiService.update(srcAttr);
              doiService.publish(doi);
            } catch (DoiException e) {
              e.printStackTrace();
            }
          }
        }
      });
    }
    System.out.printf("Created %s new source DOIs for release %s\n\n", created, release.getKey());
  }

  /**
   * Rebuilds the author (=creator) list of the release metadata from the current project and sources
   * as it is done at release time.
   */
  public void rebuildReleaseAuthors(){
    AuthorlistGenerator authGen = new AuthorlistGenerator(validator);
    // reset to project creator and then append sources & contributors
    release.setCreator(List.copyOf(project.getCreator()));
    release.setContributor(List.copyOf(project.getContributor()));
    authGen.appendSourceAuthors(release, srcDao.list(release.getKey(), null, false), settings);
    System.out.println("\n\nUPDATED RELEASE:");
    showAgents(release.getCreator());
    // store changes to release metadata
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.update(release);
    }
  }

  private static void showAgents(List<Agent> agents) {
    agents.forEach(a -> System.out.println(String.format("%s: %s [%s]", a.getName(), a.getNote(), a.getOrcid())));
  }

  /**
   * Rebuilds the source metadata from latest patches and templates.
   * Does not modify the actual release or project metadata.
   */
  public void rebuildSourceMetadata(boolean addMissingDOIs){
    System.out.printf("Rebuilt all source metadata for  %s: %s\n\n", release.getKey(), release.getTitle());

    try (SqlSession session = factory.openSession(false)) {
      DatasetSourceMapper dsm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      int cnt = dsm.deleteByRelease(release.getKey());
      session.commit();
      System.out.printf("Deleted %s old source metadata records\n", cnt);

      AtomicInteger counter = new AtomicInteger(0);
      srcDao.list(release.getKey(), release, true).forEach(d -> {
        counter.incrementAndGet();
        if (addMissingDOIs && d.getDoi() == null) {
          DOI doi = doiCfg.datasetSourceDOI(release.getKey(), d.getKey());
          d.setDoi(doi);
          System.out.printf("Creating new DOI %s for source %s\n", doi, d.getKey());
          try {
            if (!doiService.delete(doi)) {
              System.err.printf("Failed to remove DOI %s for source %s\n", doi, d.getKey());
            }
          } catch (DoiException e) {
            System.err.printf("Error removing DOI %s for source %s\n", doi, d.getKey());
            e.printStackTrace();
          }
          var srcAttr = doiUpdater.buildSourceMetadata(d, release, true);
          doiService.createSilently(srcAttr);
        }
        System.out.printf("%s: %s\n", d.getKey(), d.getCitation());
        dsm.create(release.getKey(), d);
        cm.createRelease(d.getKey(), release.getKey(), d.getAttempt());
      });
      session.commit();
      System.out.printf("Created %s new source metadata records\n", counter);
    }
  }

  public void close() {
    dataSource.close();
  }

  public static void main(String[] args) {
    PgConfig cfg = new PgConfig();
    cfg.host = "pg1.checklistbank.org";
    cfg.database = "col";
    cfg.user = "col";
    cfg.password = "";
    DoiConfig doiCfg = new DoiConfig();
    doiCfg.api = "https://api.datacite.org";
    doiCfg.prefix = "10.48580";
    doiCfg.username = "";
    doiCfg.password = "";
    try (UpdateReleaseTool reg = new UpdateReleaseTool(2351,cfg, doiCfg, 101)) { // 101=markus
      reg.rebuildReleaseAuthors();
      //reg.updateNotCurrentSourceMetadataFromLatest();
      //reg.updateSourceDOIs();
      //reg.addMissingSourceDOIs();
    }
  }
}

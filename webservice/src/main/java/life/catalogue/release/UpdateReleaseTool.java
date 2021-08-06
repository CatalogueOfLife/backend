package life.catalogue.release;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.eventbus.EventBus;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.*;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.RematcherBase;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class UpdateReleaseTool implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UpdateReleaseTool.class);

  final SqlSessionFactory factory;
  final HikariDataSource dataSource;
  final Dataset release;
  final Dataset project;
  final DatasetSettings settings;
  final int userKey;
  final DoiConfig doiCfg;
  final DoiService doiService;
  final DoiUpdater doiUpdater;

  public UpdateReleaseTool(int releaseKey, PgConfig pgCfg, DoiConfig doiCfg, int userKey) {
    dataSource = pgCfg.pool();
    this.userKey = userKey;
    factory = MybatisFactory.configure(dataSource, "tools");
    DatasetInfoCache.CACHE.setFactory(factory);

    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      release = dm.get(releaseKey);
      if (release.getOrigin() != DatasetOrigin.RELEASED) {
        throw new IllegalArgumentException("Dataset key "+releaseKey+" is not a release!");
      }
      project = dm.get(release.getSourceKey());
      settings = dm.getSettings(project.getKey());
    }
    // DOI
    this.doiCfg = doiCfg;
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig jerseyCfg = new ClientConfig(jacksonJsonProvider);
    jerseyCfg.register(new LoggingFeature(java.util.logging.Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    jerseyCfg.register(new UserAgentFilter());
    final Client client = ClientBuilder.newClient(jerseyCfg);
    doiService = new DataCiteService(doiCfg, client);
    UserDao udao = new UserDao(factory, new EventBus());
    DatasetConverter converter = new DatasetConverter(
      URI.create("https://www.catalogueoflife.org"),
      URI.create("https://data.catalogueoflife.org"),
      udao::get
    );
    LatestDatasetKeyCache cache = new LatestDatasetKeyCache(factory);
    doiUpdater = new DoiUpdater(factory, doiService, cache, converter);
  }

  /**
   * Rematches all sector targets for releases
   */
  public void rematchSectorTargets(){
    System.out.printf("Matching all sector targets of %s: %s\n\n", release.getKey(), release.getTitle());

    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    EstimateDao edao = new EstimateDao(factory);
    NameDao ndao = new NameDao(factory, indexService, NameIndexFactory.passThru());
    TaxonDao tdao = new TaxonDao(factory, ndao, indexService);
    SectorDao sdao = new SectorDao(factory, indexService, tdao);

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
   * Rebuilds the source metadata from latest patches and templates
   */
  public void rebuildSourceMetadata(boolean addMissingDOIs){
    System.out.printf("%s: %s\n\n", release.getKey(), release.getTitle());
    DatasetSourceDao dao = new DatasetSourceDao(factory);

    try (SqlSession session = factory.openSession(false)) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      int cnt = psm.deleteByRelease(release.getKey());
      session.commit();
      System.out.printf("Deleted %s old source metadata records\n", cnt);

      AtomicInteger counter = new AtomicInteger(0);
      dao.list(release.getKey(), release, true).forEach(d -> {
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
        psm.create(release.getKey(), d);
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
    cfg.host = "pg1.catalogueoflife.org";
    cfg.database = "col";
    cfg.user = "col";
    cfg.password = "";
    DoiConfig doiCfg = new DoiConfig();
    doiCfg.api = "https://api.datacite.org";
    doiCfg.prefix = "10.48580";
    doiCfg.username = "";
    doiCfg.password = "";
    try (UpdateReleaseTool reg = new UpdateReleaseTool(2328,cfg, doiCfg, 101)) { // 101=markus
      reg.rebuildSourceMetadata(true);
    }
  }
}

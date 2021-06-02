package life.catalogue.tools;

import com.zaxxer.hikari.HikariDataSource;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.*;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.RematcherBase;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class UpdateReleaseTool implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UpdateReleaseTool.class);

  final SqlSessionFactory factory;
  final HikariDataSource dataSource;
  final Dataset release;
  final Dataset project;
  final DatasetSettings settings;
  final int userKey;

  public UpdateReleaseTool(int releaseKey, PgConfig cfg, int userKey) {
    dataSource = cfg.pool();
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
  }

  /**
   * Rebuilds the source metadata from latest patches and templates
   */
  public void rebuildSourceMetadata(){
    System.out.printf("%s: %s\n\n", release.getKey(), release.getTitle());
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory);
    show(dao);
    //update(dao);
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

  void show(DatasetProjectSourceDao dao){
    System.out.printf("%s\n", release.getTitle());
    if (settings.has(Setting.RELEASE_CITATION_TEMPLATE)) {
      String citation = CitationUtils.fromTemplate(release, settings.getString(Setting.RELEASE_CITATION_TEMPLATE));
      release.setTitle(citation);
    }
    System.out.printf("%s\n", release.getTitle());
    dao.list(release.getKey(), release, true).forEach(d -> {
      System.out.printf("%s: %s\n", d.getKey(), d.getTitle());
    });
  }

  void update(DatasetProjectSourceDao dao) {
    try (SqlSession session = factory.openSession(false)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      int cnt = psm.deleteByRelease(release.getKey());
      session.commit();
      System.out.printf("Deleted %s old source metadata records\n", cnt);

      AtomicInteger counter = new AtomicInteger(0);
      dao.list(release.getKey(), release, true).forEach(d -> {
        counter.incrementAndGet();
        System.out.printf("%s: %s\n", d.getKey(), d.getTitle());
        psm.create(release.getKey(), d);
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
    cfg.host = "";
    cfg.database = "col";
    cfg.user = "col";
    cfg.password = "";
    try (UpdateReleaseTool reg = new UpdateReleaseTool(2230,cfg, 101)) { // 101=markus
      reg.rebuildSourceMetadata();
    }
  }
}

package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import life.catalogue.release.extended.SectorMerge;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.validation.Validator;

import java.util.List;

public class ExtendedRelease extends ProjectRelease {
  private final int baseReleaseKey;
  private final SectorImportDao siDao;
  private List<Sector> sectors;

  ExtendedRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, NameDao nDao, ImageService imageService,
                  int releaseKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                  DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(factory, indexService, diDao, dDao, nDao, imageService, DatasetInfoCache.CACHE.info(releaseKey, DatasetOrigin.RELEASE).sourceKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
    this.siDao = siDao;
    baseReleaseKey = releaseKey;
    LOG.info("Build extended release for project {} from public release {}", datasetKey, baseReleaseKey);
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    if (ds.has(Setting.XRELEASE_ALIAS_TEMPLATE)) {
      String alias = CitationUtils.fromTemplate(d, ds.getString(Setting.XRELEASE_ALIAS_TEMPLATE));
      d.setAlias(alias);
    }
  }

  @Override
  void prepWork() throws Exception {
    createReleaseDOI();
    try (SqlSession session = factory.openSession(true)) {
      sectors = session.getMapper(SectorMapper.class).listByPriority(datasetKey, Sector.Mode.MERGE);
    }
  }

  @Override
  protected Integer createReleaseDOI() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      // find previous public release needed for DOI management
      final Integer prevReleaseKey = session.getMapper(DatasetMapper.class).previousRelease(newDatasetKey);
      return createReleaseDOI(prevReleaseKey);
    }
  }

  @Override
  void finalWork() throws Exception {
    mergeSectors();
    homotypicGrouping();
    // update sector metrics. The entire releases metrics are done later by the superclass
    buildSectorMetrics();
    // finally also call the shared part
    super.finalWork();
  }

  /**
   * We copy the tables of the base release here, not the project
   */
  @Override
  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) {
    int count = session.getMapper(mapperClass).copyDataset(baseReleaseKey, newDatasetKey, false);
    LOG.info("Copied {} {}s", count, entity.getSimpleName());
  }

  /**
   * This updates the merge sector metrics with the final counts.
   * We do this at the very end as homotypic grouping and other final changes have impact on the sectors.
   */
  private void buildSectorMetrics() {
    // sector metrics
    metrics.setState( ImportState.ANALYZING);
    for (Sector s : sectors) {
      var sim = siDao.getAttempt(s, s.getSyncAttempt());
      LOG.info("Build metrics for sector {}", s);
      siDao.updateMetrics(sim, newDatasetKey);
    }
  }

  /**
   * We do all extended work here, e.g. sector merging
   */
  private void mergeSectors() throws Exception {
    metrics.setState(ImportState.INSERTING);
    for (Sector s : sectors) {
      checkIfCancelled();
      var sm = new SectorMerge(newDatasetKey, s, getUserKey(), factory, siDao);
      sm.run();
    }
  }

  private void homotypicGrouping() {
    LOG.info("Start homotypic grouping of names");
  }
}

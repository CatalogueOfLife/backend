package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.cache.VarnishUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.validation.Validator;
import javax.ws.rs.core.UriBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ExtendedRelease extends ProjectRelease {
  final int sourceReleaseKey;
  ExtendedRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, NameDao nDao, ImageService imageService,
                  int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                  DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(factory, indexService, diDao, dDao, nDao, imageService, datasetKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
    sourceReleaseKey = 0;
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
  }

  @Override
  void finalWork() throws Exception {
    extendRelease();
    // finally also call the shared part
    super.finalWork();
  }

  @Override
  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) {
    int count = session.getMapper(mapperClass).copyDataset(sourceReleaseKey, newDatasetKey, false);
    LOG.info("Copied {} {}s", count, entity.getSimpleName());
  }

  /**
   * We do all extended work here, e.g. sector merging
   */
  private void extendRelease() {
    List<Sector> sectors;
    try (SqlSession session = factory.openSession(true)) {
      sectors = session.getMapper(SectorMapper.class).listByPriority(datasetKey, Sector.Mode.MERGE);
    }
    for (Sector s : sectors) {
      mergeSector(s);
    }
  }

  private void mergeSector(Sector s) {
    LOG.info("Merge sector {}", s);
  }
}

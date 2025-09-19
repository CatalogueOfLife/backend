package life.catalogue.release;

import life.catalogue.assembly.SyncFactory;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.UsageMatcherGlobal;

import java.net.URI;

import life.catalogue.matching.nidx.NameIndex;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;

/**
 * Temp class to debug XRelease problem with COL.
 * A regular, full XR takes 2 days which we want to bring down to the essentials to debug and repeat quicker.
 */
public class XReleaseDebug extends XRelease{
  private static final int SECTOR_SYNCS_LIMIT = 4;

  public XReleaseDebug(SqlSessionFactory factory, SyncFactory syncFactory, NameIndex nidx, NameUsageIndexService indexService, ImageService imageService, DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao, int releaseKey, int userKey, ReleaseConfig cfg, DoiConfig doiCfg, URI apiURI, URI clbURI, CloseableHttpClient client, ExportManager exportManager, DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(factory, syncFactory, nidx, indexService, imageService, dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey, cfg, doiCfg, apiURI, clbURI, client, exportManager, doiService, doiUpdater, validator);
  }

  @Override
  protected void metrics() throws InterruptedException {
    super.metrics();
  }

  @Override
  void finalWork() throws Exception {
    super.finalWork();
  }

  @Override
  protected void homotypicGrouping() throws InterruptedException {
    // skip
  }

  @Override
  protected void flagLoops() throws InterruptedException {
    // skip
  }

  @Override
  protected void mergeSectors() throws Exception {
    mergeSectors(SECTOR_SYNCS_LIMIT);
  }

  @Override
  protected void validateAndCleanTree() throws InterruptedException {
    // skip
  }

  @Override
  protected void cleanImplicitTaxa() throws InterruptedException {
    // skip
  }
}

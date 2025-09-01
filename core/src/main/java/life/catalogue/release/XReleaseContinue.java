package life.catalogue.release;

import jakarta.validation.Validator;

import jakarta.ws.rs.core.UriBuilder;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.UsageMatcherGlobal;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Temp class to debug XRelease problem with COL.
 * A regular, full XR takes 2 days which we want to bring down to the essentials to debug and repeat quicker.
 */
public class XReleaseContinue extends XRelease{
  private static final Logger LOG = LoggerFactory.getLogger(XReleaseContinue.class);

  public XReleaseContinue(SqlSessionFactory factory, SyncFactory syncFactory, UsageMatcherGlobal matcher, NameUsageIndexService indexService, ImageService imageService,
                          DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                          int releaseKey, final int tmpKey, int userKey, ReleaseConfig cfg, DoiConfig doiCfg,
                          URI apiURI, URI clbURI, CloseableHttpClient client, ExportManager exportManager, DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(factory, syncFactory, matcher, indexService, imageService, dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey, cfg, doiCfg, apiURI, clbURI, client, exportManager, doiService, doiUpdater, validator);
    this.tmpProjectKey = tmpKey;
    idMapDatasetKey = tmpKey;
  }

  @Override
  void initJob() throws Exception {
    // new import/release attempt
    metrics = diDao.createWaiting(projectKey, this, user);
    metrics.setJob(getClass().getSimpleName());
    attempt = metrics.getAttempt();
    LoggingUtils.setDatasetMDC(projectKey, attempt, getClass());
    updateState(ImportState.PREPARING);

    // create new dataset, e.g. release
    newDataset = dDao.copy(projectKey, user, this::modifyDataset);
    newDatasetKey = newDataset.getKey();

    // point to release in CLB - this requires the datasetKey to exist already
    newDataset.setUrl(UriBuilder.fromUri(clbURI)
      .path("dataset")
      .path(newDataset.getKey().toString())
      .build());
    dDao.update(newDataset, user);

  }

  @Override
  public void runWithLock() throws Exception {
    initJob();

    checkIfCancelled();
    LOG.info("{} project {} to new dataset {}", actionName, projectKey, newDatasetKey);
    // prepare new tables
    updateState(ImportState.PROCESSING);

    // are sequences in place?
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(newDatasetKey);;
    }

    // instead of prepWork();
    checkIfCancelled();
    loadMergeSectors();
    updateMetadata();
    cfg.restart = 10_000_000;
    usageIdGen = new XIdProvider(projectKey, tmpProjectKey, attempt, newDatasetKey, cfg, prCfg, ni, factory);

    // copy data
    checkIfCancelled();
    copyData();

    // subclass specifics
    checkIfCancelled();
    finalWork();

    // remove sequences if not a project
    if (newDataset.getOrigin() != DatasetOrigin.PROJECT) {
      LOG.info("Removing db sequences for {} {}", newDataset.getOrigin(), newDatasetKey);
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(DatasetPartitionMapper.class).createSequences(newDatasetKey);
      }
    }

    checkIfCancelled();
    metrics();
    checkIfCancelled();

    try {
      // ES index
      LOG.info("Index dataset {} into ES", newDatasetKey);
      updateState(ImportState.INDEXING);
      index();
    } catch (Exception e) {
      // allow indexing to fail - sth we can do afterwards again
      LOG.error("Error indexing new dataset {} into ES. Source dataset={}", newDatasetKey, projectKey, e);
    }

    metrics.setState(ImportState.FINISHED);
    LOG.info("Successfully finished {} project {} into dataset {}", actionName, projectKey, newDatasetKey);
  }

}

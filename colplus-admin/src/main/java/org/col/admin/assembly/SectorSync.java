package org.col.admin.assembly;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.common.util.LoggingUtils;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.col.es.NameUsageIndexServiceES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1. get all decisions for the source
 * 2. iterate over taxa & syns
 */
public class SectorSync implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);

  // sources
  private final int datasetKey;
  private final Sector sector;
  
  private final SqlSessionFactory factory;
  private final NameUsageIndexServiceES indexService;
  private Map<String, EditorialDecision> decisions = new HashMap<>();
  private final Consumer<SectorSync> successCallback;
  private final BiConsumer<SectorSync, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  private LocalDateTime started;
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceES indexService,
                    Consumer<SectorSync> successCallback,
                    BiConsumer<SectorSync, Exception> errorCallback) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      this.sector = s;
      this.datasetKey = session.getMapper(ColSourceMapper.class).get(sector.getColSourceKey()).getDatasetKey();
    }
    this.factory = factory;
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
  @Override
  public void run() {
    LoggingUtils.setMDC(datasetKey, getClass());
    try {
      started = LocalDateTime.now();
      sync();
      successCallback.accept(this);
  
    } catch (Exception e) {
      errorCallback.accept(this, e);
      
    } finally {
      LoggingUtils.removeMDC();
    }
  }
  
  private void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Sync of sector " + sector.getKey() + " was cancelled");
    }
  }
  
  public Integer getSectorKey() {
    return sector.getKey();
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public LocalDateTime getStarted() {
    return started;
  }
  
  public void sync() throws InterruptedException {
    loadDecisions();
    checkIfCancelled();
    
    processTree();
    checkIfCancelled();

    deleteOld();
    checkIfCancelled();

    updateSearchIndex();
  }
  
  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (EditorialDecision ed : dm.listByDataset(datasetKey)) {
        decisions.put(ed.getSubject().getId(), ed);
      }
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sector.getKey());
  }
  
  private void processTree() {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      final Set<String> blockedIds = decisions.values().stream()
          .filter(ed -> ed.getMode().equals(EditorialDecision.Mode.BLOCK))
          .map(ed -> ed.getSubject().getId())
          .collect(Collectors.toSet());
      LOG.info("Traverse taxon tree, blocking {} nodes", blockedIds.size());
      final TreeCopyHandler treeHandler = new TreeCopyHandler(session);
      tm.processTree(datasetKey, sector.getSubject().getId(), blockedIds, treeHandler);
      session.commit();
    }
  }
  
  class TreeCopyHandler implements ResultHandler<Taxon> {
    final SqlSession session;
    final TaxonDao dao;
    final SynonymMapper sMapper;
    int counter = 0;
  
    TreeCopyHandler(SqlSession session) {
      this.session = session;
      dao = new TaxonDao(session);
      sMapper = session.getMapper(SynonymMapper.class);
    }
  
    @Override
    public void handleResult(ResultContext<? extends Taxon> ctxt) {
      Taxon tax = ctxt.getResultObject();
      // TODO: copy/update name, taxon, refs, vernaculars, distributions
      //dao.copy()
      for (Synonym syn : sMapper.listByTaxon(tax.getDatasetKey(), tax.getId())) {
        // TODO: copy name, syn, refs
        
      }
      
      // commit in batches
      if (counter % 100 == 0) {
        session.commit();
      }
    }
  }
  
  private void deleteOld() {
    LOG.info("Deleting old taxa, synonym and orphaned names from the same sector");
    //TODO: delete by sectorKey and date last modified < this.created
  }
  
  private void updateSearchIndex() {
    LOG.info("Update search index");
  }
  
}

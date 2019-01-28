package org.col.admin.assembly;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.EntityType;
import org.col.common.util.LoggingUtils;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.col.es.NameUsageIndexServiceEs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1. get all decisions for the source
 * 2. iterate over taxa & syns
 */
public class SectorSync implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);
  private static Set<EntityType> COPY_DATA = ImmutableSet.of(
      EntityType.REFERENCE,
      EntityType.VERNACULAR,
      EntityType.DISTRIBUTION
  );

  // sources
  private final int datasetKey;
  private final Sector sector;
  // target
  private final int catalogueKey = Datasets.DRAFT_COL;
  
  private final SqlSessionFactory factory;
  private final NameUsageIndexServiceEs indexService;
  private Map<String, EditorialDecision> decisions = new HashMap<>();
  private final Consumer<SectorSync> successCallback;
  private final BiConsumer<SectorSync, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  private final ColUser user;
  private final SectorSyncState state = new SectorSyncState();
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                    Consumer<SectorSync> successCallback,
                    BiConsumer<SectorSync, Exception> errorCallback, ColUser user) {
    this.user = user;
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      this.sector = s;
      this.datasetKey = sector.getDatasetKey();
      Taxon target = session.getMapper(TaxonMapper.class).get(catalogueKey, sector.getTarget().getId());
      if (target == null) {
        throw new IllegalStateException("Sector " + sectorKey + " does have a non existing target id for catalogue " + catalogueKey);
      }
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
      state.started = LocalDateTime.now();
      
      sync();
      successCallback.accept(this);
  
    } catch (Exception e) {
      errorCallback.accept(this, e);
      
    } finally {
      LoggingUtils.removeMDC();
    }
  }
  
  public SectorSyncState getState() {
    return state;
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
    return state.started;
  }
  
  public void sync() throws InterruptedException {
    state.status = SectorSyncState.Status.PREPARING;
    loadDecisions();
    checkIfCancelled();
  
    state.status = SectorSyncState.Status.COPYING;
    processTree();
    checkIfCancelled();
  
    state.status = SectorSyncState.Status.DELETING;
    deleteOld();
    checkIfCancelled();
  
    state.status = SectorSyncState.Status.INDEXING;
    updateSearchIndex();

    state.status = SectorSyncState.Status.FINISHED;
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
    final Map<String, String> ids = new HashMap<>();
  
    TreeCopyHandler(SqlSession session) {
      this.session = session;
      dao = new TaxonDao(session);
      sMapper = session.getMapper(SynonymMapper.class);
    }
  
    @Override
    public void handleResult(ResultContext<? extends Taxon> ctxt) {
      Taxon tax = ctxt.getResultObject();
      // lookup parent
      String parentID = sector.getSubject().getId().equals(tax.getId()) ?
          sector.getTarget().getId() :
          ids.get(tax.getParentId());
      // copy name, taxon, refs, vernaculars, distributions
      DatasetID orig = dao.copyTaxon(tax, catalogueKey, parentID, user, COPY_DATA, this::lookupReference);
      // remember old to new id mapping
      ids.put(orig.getId(), tax.getId());
      DatasetID acc = new DatasetID(tax);
      for (Synonym syn : sMapper.listByTaxon(tax.getDatasetKey(), tax.getId())) {
        // copy synonym, name, syn, refs
        dao.copySynonym(syn, acc, user);
      }
      // commit in batches
      if (counter++ % 100 == 0) {
        session.commit();
      }
      state.created.set(counter);
    }
    
    private String lookupReference(Reference ref) {
      if (ref != null) {
        //TODO: lookup existing refs from other sectors
        if (1 == 2) {
          ref.setDatasetKey(catalogueKey);
          ref.applyUser(user);
          // TODO: Create ref
        }
      }
      return null;
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

package org.col.admin.assembly;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
import org.col.api.vocab.Issue;
import org.col.common.util.LoggingUtils;
import org.col.db.dao.DatasetImportDao;
import org.col.db.dao.MatchingDao;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.col.db.printer.TextTreePrinter;
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
  // maps keyed on taxon ids from this sector
  private final Map<String, EditorialDecision> decisions = new HashMap<>();
  private final Map<String, String> foreignChildren = new HashMap<>();
  private List<Sector> childSectors;
  private final Consumer<SectorSync> successCallback;
  private final BiConsumer<SectorSync, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  private final ColUser user;
  private final SectorImport state = new SectorImport();
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                    Consumer<SectorSync> successCallback,
                    BiConsumer<SectorSync, Exception> errorCallback, ColUser user) {
    this.user = user;
    try (SqlSession session = factory.openSession(true)) {
      // check if sector actually exists
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      this.sector = s;
      this.datasetKey = sector.getDatasetKey();
      // check if target actually exists
      Taxon target = session.getMapper(TaxonMapper.class).get(catalogueKey, sector.getTarget().getId());
      if (target == null) {
        throw new IllegalStateException("Sector " + sectorKey + " does have a non existing target id for catalogue " + catalogueKey);
      }
      // lookup next attempt
      List<SectorImport> imports = session.getMapper(SectorImportMapper.class).list(sectorKey, null, new Page(0,1));
      state.setAttempt(imports == null || imports.isEmpty() ? 1 : imports.get(0).getAttempt() + 1);
      state.setSectorKey(sectorKey);
      state.setState(SectorImport.State.WAITING);
    }
    this.factory = factory;
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
 
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(datasetKey, 0, getClass());
    try {
      state.setStarted(LocalDateTime.now());
      
      sync();
      metrics();
      successCallback.accept(this);
  
    } catch (InterruptedException e) {
      state.setState(SectorImport.State.CANCELED);
      errorCallback.accept(this, e);

    } catch (Exception e) {
      state.setState(SectorImport.State.FAILED);
      state.setError(e.getCause().getMessage());
      errorCallback.accept(this, e);
      
    } finally {
      state.setFinished(LocalDateTime.now());
      try (SqlSession session = factory.openSession(true)) {
        SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
        sim.create(state);
      }
      LoggingUtils.removeSectorMDC();
    }
  }
  
  private void metrics() {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      state.setDescriptionCount(sim.countDescription(catalogueKey, sector.getKey()));
      state.setDistributionCount(sim.countDistribution(catalogueKey, sector.getKey()));
      state.setMediaCount(sim.countMedia(catalogueKey, sector.getKey()));
      state.setNameCount(sim.countName(catalogueKey, sector.getKey()));
      state.setReferenceCount(sim.countReference(catalogueKey, sector.getKey()));
      state.setTaxonCount(sim.countTaxon(catalogueKey, sector.getKey()));
      state.setVernacularCount(sim.countVernacular(catalogueKey, sector.getKey()));
      state.setIssueCount(DatasetImportDao.countMap(Issue.class, sim.countIssues(catalogueKey, sector.getKey())));
      //TODO: usagesByRankCount

      try {
        StringWriter tree = new StringWriter();
        TextTreePrinter.sector(catalogueKey, sector.getKey(), factory, tree).print();
        state.setTextTree(tree.toString());
      } catch (IOException e) {
        LOG.error("Failed to print sector {} of catalogue {}", sector.getKey(), catalogueKey, e);
      }
      state.setNames(session.getMapper(NameMapper.class).listNameIndexIds(datasetKey, sector.getKey()));
      sim.create(state);
    }
  }
  
  public SectorImport getState() {
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
    return state.getStarted();
  }
  
  public void sync() throws InterruptedException {
    state.setState( SectorImport.State.PREPARING);
    loadDecisions();
    loadForeignChildren();
    loadAttachedSectors();
    checkIfCancelled();

    state.setState( SectorImport.State.DELETING);
    deleteOld();
    checkIfCancelled();

    state.setState( SectorImport.State.COPYING);
    processTree();
    checkIfCancelled();
  
    state.setState( SectorImport.State.RELINKING);
    relinkForeignChildren();
    relinkAttachedSectors();
  
    state.setState( SectorImport.State.INDEXING);
    updateSearchIndex();
  
    state.setState( SectorImport.State.FINISHED);
  }
  
  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (EditorialDecision ed : dm.list(datasetKey, null)) {
        decisions.put(ed.getSubject().getId(), ed);
      }
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sector.getKey());
  }
  
  private void loadForeignChildren() {
    try (SqlSession session = factory.openSession(true)) {
      //TODO: implement
    }
    LOG.info("Loaded {} children from other sectors with a parent from sector {}", foreignChildren.size(), sector.getKey());
  }
  
  private void loadAttachedSectors() {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      childSectors=sm.listChildSectors(sector.getKey());
    }
    LOG.info("Loaded {} sectors targeting taxa from sector {}", childSectors.size(), sector.getKey());
  }
  
  
  private void relinkForeignChildren() {
    //TODO
  }
  
  private void relinkAttachedSectors() {
    try (SqlSession session = factory.openSession(false)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      MatchingDao mdao = new MatchingDao(session);
      for (Sector s : childSectors) {
        List<Taxon> matches = mdao.match(s.getTarget(), sector.getKey());
        if (matches.isEmpty()) {
          LOG.warn("Child sector {} cannot be rematched to synced sector {} - lost {}", s.getKey(), sector.getKey(), s.getTarget());
          //TODO: warn in sync status !!!
        } else if (matches.size() > 1) {
          LOG.warn("Child sector {} cannot be rematched to synced sector {} - multiple names like {}", s.getKey(), sector.getKey(), s.getTarget());
          //TODO: warn in sync status !!!
        } else {
          s.getTarget().setId(matches.get(0).getId());
          sm.update(s);
        }
      }
      session.commit();
    }
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
      tm.processTree(datasetKey, null, sector.getSubject().getId(), blockedIds, false, treeHandler);
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
      tax.setSectorKey(sector.getKey());
      tax.getName().setSectorKey(sector.getKey());
      
      String parentID;
      // treat root node according to sector mode
      if (sector.getSubject().getId().equals(tax.getId())) {
        if (sector.getMode() == Sector.Mode.MERGE) {
          // in merge mode the root node itself is not copied
          // but all child taxa should be linked to the sector target, so remember ID:
          ids.put(tax.getId(), sector.getTarget().getId());
          return;
        }
        // we want to attach the root node under the sector target
        parentID = sector.getTarget().getId();
      } else {
        // all non root nodes have newly created parents
        parentID = ids.get(tax.getParentId());
      }

      // Taxon: copy name, taxon, refs, vernaculars, distributions
      DatasetID orig = dao.copyTaxon(tax, catalogueKey, parentID, user, COPY_DATA, this::lookupReference);
      // remember old to new id mapping
      ids.put(orig.getId(), tax.getId());
      
      // Synonyms
      DatasetID acc = new DatasetID(tax);
      for (Synonym syn : sMapper.listByTaxon(tax.getDatasetKey(), tax.getId())) {
        // copy synonym, name, syn, refs
        dao.copySynonym(syn, acc, user);
      }
      
      // commit in batches
      if (counter++ % 100 == 0) {
        session.commit();
      }
      state.setTaxonCount(counter);
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
    int count;
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      count = tm.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sector.getKey());
    }
    
    // TODO: delete orphaned names
    count = 0;
    LOG.info("Deleted {} orphaned names from sector {}", count, sector.getKey());
  }
  
  private void updateSearchIndex() {
    LOG.info("Update search index");
  }
  
}

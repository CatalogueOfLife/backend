package org.col.admin.assembly;

import java.io.IOException;
import java.io.StringWriter;
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
import org.col.api.vocab.EntityType;
import org.col.api.vocab.Issue;
import org.col.db.dao.DatasetImportDao;
import org.col.db.dao.MatchingDao;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.col.db.printer.TextTreePrinter;
import org.col.es.NameUsageIndexServiceEs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Syncs/imports source data for a given sector into the assembled catalgoue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);
  private static Set<EntityType> COPY_DATA = ImmutableSet.of(
      EntityType.REFERENCE,
      EntityType.VERNACULAR,
      EntityType.DISTRIBUTION
  );
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                    Consumer<SectorRunnable> successCallback,
                    BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    super(sectorKey, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() throws Exception {
    sync();
    metrics();
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
    }
  }
  
  private void sync() throws InterruptedException {

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
  
      if (decisions.containsKey(tax.getId())) {
        applyDecision(tax, decisions.get(tax.getId()));
      }
      
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
        if (syn.getId() != null && decisions.containsKey(syn.getId())) {
          applyDecision(syn, decisions.get(syn.getId()));
        }
        // copy synonym, name, syn, refs
        dao.copySynonym(syn, acc, user);
      }
      
      // commit in batches
      if (counter++ % 100 == 0) {
        session.commit();
      }
      state.setTaxonCount(counter);
    }
    
    private void applyDecision(Taxon tax, EditorialDecision ed) {
      switch (ed.getMode()) {
        case BLOCK:
          throw new IllegalStateException("Blocked taxon "+tax.getId()+" should not have been traversed");
        case CHRESONYM:
          //TODO: we need to exclude the name from CoL. Watch out for linking children correctly
        case UPDATE:
          updateUsage(tax, ed);
      }
    }
  
    private void applyDecision(Synonym syn, EditorialDecision ed) {
      switch (ed.getMode()) {
        case BLOCK:
          //TODO: we need to exclude the name from CoL.
        case CHRESONYM:
          //TODO: we need to exclude the name from CoL.
          break;
        case UPDATE:
          updateUsage(syn, ed);
      }
    }
  
    private void updateUsage(NameUsage u, EditorialDecision ed) {
      if (ed.getName() != null) {
        //TODO: update usage
      }
      if (ed.getStatus() != null) {
        //TODO: update usage
      }
      if (ed.getLifezones() != null) {
        //TODO: update usage and all descendants
      }
      if (ed.getFossil() != null) {
        //TODO: update usage and all descendants
      }
      if (ed.getRecent() != null) {
        //TODO: update usage and all descendants
      }
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

package org.col.importer;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Users;
import org.col.common.lang.InterruptedRuntimeException;
import org.col.config.ImporterConfig;
import org.col.db.mapper.*;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.model.Labels;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RelType;
import org.col.importer.neo.traverse.StartEndHandler;
import org.col.importer.neo.traverse.TreeWalker;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class PgImport implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);
  
  private final NeoDb store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final Dataset dataset;
  private BiMap<Integer, Integer> verbatimKeys = HashBiMap.create();
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger sCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger diCounter = new AtomicInteger(0);
  private final AtomicInteger deCounter = new AtomicInteger(0);
  private final AtomicInteger mCounter = new AtomicInteger(0);
  private final AtomicInteger vCounter = new AtomicInteger(0);
  
  public PgImport(int datasetKey, NeoDb store, SqlSessionFactory sessionFactory,
                  ImporterConfig cfg) {
    this.dataset = store.getDataset();
    this.dataset.setKey(datasetKey);
    this.store = store;
    this.batchSize = cfg.batchSize;
    this.sessionFactory = sessionFactory;
  }
  
  @Override
  public Boolean call() throws InterruptedException, InterruptedRuntimeException {
    partition();
    
    insertVerbatim();
    
    insertReferences();
    
    insertNames();
    
    insertNameRelations();
    
		insertUsages();

    attach();
    
    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} synonyms, {} references, {} vernaculars, {} distributions, {} descriptions and {} media items",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, sCounter, rCounter, vCounter, diCounter, deCounter, mCounter);
		return true;
	}

  private void partition() throws InterruptedException {
    interruptIfCancelled();
    try (SqlSession session = sessionFactory.openSession(false)) {
      partition(session, dataset.getKey());
    }
  }
  
  /**
   * Creates all dataset partitions needed, removing any previous partition and data for the given datasetKey.
   * To avoid table deadlocks we synchronize this method!
   * See https://github.com/Sp2000/colplus-backend/issues/127
   */
  static synchronized void partition(SqlSession session, int datasetKey) {
    LOG.info("Create empty partition for dataset {}", datasetKey);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    // first remove
    mapper.delete(datasetKey);
    
    // then create
    mapper.create(datasetKey);
    session.commit();
  }
  
  /**
   * Builds indices and finally attaches partitions to main tables.
   * To avoid table deadlocks on the main table we synchronize this method.
   */
  static synchronized void attach(SqlSession session, int datasetKey) {
      LOG.info("Build partition indices for dataset {}", datasetKey);
      DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    
      // build indices and add dataset bound constraints
      mapper.buildIndices(datasetKey);
    
      // attach to main table
      mapper.attach(datasetKey);
      session.commit();
  }
  
  /**
   * Builds indices and finally attaches partitions to main tables.
   * To avoid table deadlocks on the main table we synchronize this method.
   */
  private synchronized void attach() {
    interruptIfCancelled();
    try (SqlSession session = sessionFactory.openSession(true)) {
      attach(session, dataset.getKey());
    }
  }
  
  private void updateMetadata() {
    try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      Dataset old = mapper.get(dataset.getKey());
      if (dataset.getTitle() != null) {
        // make sure we keep a title even if old
        old.setTitle(dataset.getTitle());
      }
      old.setAuthorsAndEditors(dataset.getAuthorsAndEditors());
      old.setContact(dataset.getContact());
      old.setDescription(dataset.getDescription());
      old.setWebsite(dataset.getWebsite());
      old.setLicense(dataset.getLicense());
      old.setOrganisations(dataset.getOrganisations());
      old.setReleased(dataset.getReleased());
      old.setVersion(dataset.getVersion());
      
      mapper.update(old);
      session.commit();
    }
  }
  
  private void insertVerbatim() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
      int counter = 0;
      Map<Integer, VerbatimRecord> batchCache = new HashMap<>();
      for (VerbatimRecord v : store.verbatimList()) {
        int storeKey = v.getKey();
        v.setKey(null);
        v.setDatasetKey(dataset.getKey());
        mapper.create(v);
        batchCache.put(storeKey, v);
        if (++counter % batchSize == 0) {
          commitVerbatimBatch(session, batchCache);
          LOG.debug("Inserted {} verbatim records so far", counter);
        }
      }
      commitVerbatimBatch(session, batchCache);
      LOG.info("Inserted {} verbatim records", counter);
    }
  }
  
  private void commitVerbatimBatch(SqlSession session, Map<Integer, VerbatimRecord> batchCache) {
    interruptIfCancelled();
    session.commit();
    // we only get the new keys after we committed in batch mode!!!
    for (Map.Entry<Integer, VerbatimRecord> e : batchCache.entrySet()) {
      verbatimKeys.put(e.getKey(), e.getValue().getKey());
    }
    batchCache.clear();
  }
  
  private <T extends VerbatimEntity & UserManaged> T updateVerbatimUserEntity(T ent) {
    return updateUser(updateVerbatimEntity(ent));
  }
  
  private <T extends VerbatimEntity> T updateVerbatimEntity(T ent) {
    if (ent != null && ent.getVerbatimKey() != null) {
      ent.setVerbatimKey(verbatimKeys.get(ent.getVerbatimKey()));
    }
    return ent;
  }

  private static <T extends UserManaged> T updateUser(T ent) {
    if (ent != null) {
      ent.setCreatedBy(Users.IMPORTER);
      ent.setModifiedBy(Users.IMPORTER);
    }
    return ent;
  }

  private void insertReferences() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        r.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(r);
        updateUser(r);
        mapper.create(r);
        rCounter.incrementAndGet();
        if (counter++ % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} references", counter);
        }
      }
      session.commit();
      LOG.debug("Inserted all {} references", counter);
    }
  }
  
  
  /**
   * Inserts all names, collecting all homotypic name keys for later updates if they havent been inserted already.
   */
  private void insertNames() {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.names().all().forEach(n -> {
        n.name.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(n.name);
        nameMapper.create(n.name);
        if (nCounter.incrementAndGet() % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} other names", nCounter.get());
        }
      });
      session.commit();
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }
  
  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertNameRelations() {
    for (RelType rt : RelType.values()) {
      if (!rt.isNameRel()) continue;

      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
        final NameRelationMapper nameRelationMapper = session.getMapper(NameRelationMapper.class);
        LOG.debug("Inserting all {} relations", rt);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            NameRelation nr = store.toRelation(rel);
            nameRelationMapper.create(updateUser(nr));
            if (counter.incrementAndGet() % batchSize == 0) {
              interruptIfCancelled();
              session.commit();
            }
          });
        }
        session.commit();
      }
      LOG.info("Inserted {} {} relations", counter.get(), rt);
    }
  }

	/**
	 * insert taxa/synonyms with all the rest
	 */
	private void insertUsages() throws InterruptedException {
		try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH,false)) {
      LOG.info("Inserting remaining names and all taxa");
      DescriptionMapper descriptionMapper = session.getMapper(DescriptionMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      MediaMapper mediaMapper = session.getMapper(MediaMapper.class);
      ReferenceMapper refMapper = session.getMapper(ReferenceMapper.class);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte synonyms will be visited multiple times, remember their name ids!
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<String> parentIds = new Stack<>();
        
        @Override
        public void start(Node n) {
          NeoUsage u = store.usages().objByNode(n);
          NeoName nn = store.nameByUsage(n);
          updateVerbatimEntity(u);
          updateVerbatimEntity(nn);

          // update share props for taxon or synonym
          NameUsageBase nu = u.usage;
          nu.setName(nn.name);
          nu.setDatasetKey(dataset.getKey());
          updateUser(nu);
          if (!parentIds.empty()) {
            // use parent postgres key from stack, but keep it there
            nu.setParentId(parentIds.peek());
          } else if (u.isSynonym()) {
            throw new IllegalStateException("Synonym node " + n.getId() + " without accepted taxon found: " + nn.name.getScientificName());
          } else if (!n.hasLabel(Labels.ROOT)) {
            throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + nn.name.getScientificName());
          }
  
          // insert taxon or synonym
          if (u.isSynonym()) {
            synMapper.create(u.getSynonym());
            sCounter.incrementAndGet();

          } else {
            Taxon tax = u.getTaxon();
            taxonMapper.create(updateUser(tax));
            tCounter.incrementAndGet();
            String taxonId = tax.getId();

            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentIds.push(taxonId);
            
            // insert vernacular
            for (VernacularName vn : u.vernacularNames) {
              updateVerbatimUserEntity(vn);
              vernacularMapper.create(vn, taxonId, dataset.getKey());
              vCounter.incrementAndGet();
            }
            
            // insert distributions
            for (Distribution d : u.distributions) {
              updateVerbatimUserEntity(d);
              distributionMapper.create(d, taxonId, dataset.getKey());
              diCounter.incrementAndGet();
            }
  
            // insert descriptions
            for (Description d : u.descriptions) {
              updateVerbatimUserEntity(d);
              descriptionMapper.create(d, taxonId, dataset.getKey());
              deCounter.incrementAndGet();
            }
  
            // insert media
            for (Media m : u.media) {
              updateVerbatimUserEntity(m);
              mediaMapper.create(m, taxonId, dataset.getKey());
              mCounter.incrementAndGet();
            }
            
            // link bibliography
            for (String id : u.bibliography) {
              refMapper.linkToTaxon(dataset.getKey(), taxonId, id);
            }
          }
          
          // commit in batches
          if (counter++ % batchSize == 0) {
            interruptIfCancelled();
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }
        
        @Override
        public void end(Node n) {
          interruptIfCancelled();
          // remove this key from parent queue if its an accepted taxon
          if (n.hasLabel(Labels.TAXON)) {
            parentIds.pop();
          }
        }
      });
      session.commit();
      LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);
    }
  }
  
}

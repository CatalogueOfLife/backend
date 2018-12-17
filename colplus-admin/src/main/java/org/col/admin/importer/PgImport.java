package org.col.admin.importer;

import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.ImporterConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.neo.traverse.StartEndHandler;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.col.api.model.*;
import org.col.api.vocab.Users;
import org.col.db.dao.NameDao;
import org.col.db.mapper.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  
  private void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("PgImport was cancelled/interrupted");
    }
  }
  
  @Override
  public Boolean call() throws InterruptedException {
    checkIfCancelled();
    partition();
    
    checkIfCancelled();
    insertVerbatim();
    
    checkIfCancelled();
    insertReferences();
    
    checkIfCancelled();
    insertNames();
    
    checkIfCancelled();
    insertNameRelations();
    
    checkIfCancelled();
		insertUsages();

    checkIfCancelled();
    attach();
    
    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} references, {} vernaculars, {} distributions, {} descriptions and {} media items",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, rCounter, vCounter, diCounter, deCounter, mCounter);
		return true;
	}

  private void partition(){
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
    session.commit();
    
    // then create
    mapper.create(datasetKey);
    session.commit();
  }
  
  /**
   * Builds indices and finally attaches partitions to main tables.
   */
  private void attach() {
    try (SqlSession session = sessionFactory.openSession(true)) {
      LOG.info("Build partition indices for dataset {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
      
      mapper.buildIndices(dataset.getKey());
      session.commit();
      
      mapper.attach(dataset.getKey());
      session.commit();
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
    try (final SqlSession session = sessionFactory.openSession(false)) {
      VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
      int counter = 0;
      for (VerbatimRecord v : store.verbatimList()) {
        int storeKey = v.getKey();
        v.setKey(null);
        v.setDatasetKey(dataset.getKey());
        mapper.create(v);
        verbatimKeys.put(storeKey, v.getKey());
        if (++counter % batchSize == 0) {
          checkIfCancelled();
          session.commit();
          LOG.debug("Inserted {} verbatim records so far", counter);
        }
      }
      session.commit();
      LOG.info("Inserted {} verbatim records", counter);
    }
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
      ent.setCreatedBy(Users.MATCHER);
      ent.setModifiedBy(Users.MATCHER);
    }
    return ent;
  }

  private void insertReferences() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        r.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(r);
        updateUser(r);
        mapper.create(r);
        rCounter.incrementAndGet();
        if (counter++ % batchSize == 0) {
          checkIfCancelled();
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
  private void insertNames() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.names().all().forEach(n -> {
        if (n.name.getHomotypicNameId() == null) {
          n.name.setHomotypicNameId(n.name.getId());
        }
        n.name.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(n.name);
        nameMapper.create(n.name);
        if (nCounter.incrementAndGet() % batchSize == 0) {
          session.commit();
          LOG.debug("Inserted {} other names", nCounter.get());
        };
      });
      session.commit();
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }
  
  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertNameRelations() throws InterruptedException {
    for (RelType rt : RelType.values()) {
      if (!rt.isNameRel()) continue;

      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(false)) {
        final NameRelationMapper nameRelationMapper = session.getMapper(NameRelationMapper.class);
        LOG.debug("Inserting all {} relations", rt);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            NameRelation nr = store.toRelation(rel);
            nameRelationMapper.create(updateUser(nr));
            if (counter.incrementAndGet() % batchSize == 0) {
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
		try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Inserting remaining names and all taxa");
      NameDao nameDao = new NameDao(session);
      DescriptionMapper descriptionMapper = session.getMapper(DescriptionMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      MediaMapper mediaMapper = session.getMapper(MediaMapper.class);
      ReferenceMapper refMapper = session.getMapper(ReferenceMapper.class);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
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

          // insert accepted taxon or synonym
          String taxonId;
          if (u.isSynonym()) {
            taxonId = parentIds.peek();
            nameDao.addSynonym(dataset.getKey(), nn.name.getId(), taxonId, updateUser(u.getSynonym()));

          } else {
            Taxon tax = u.getTaxon();
            if (!parentIds.empty()) {
              // use parent postgres key from stack, but keep it there
              tax.setParentId(parentIds.peek());
            } else if (!n.hasLabel(Labels.ROOT)) {
              throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + nn.name.getScientificName());
            }

            tax.setName(nn.name);
            tax.setDatasetKey(dataset.getKey());
            taxonMapper.create(updateUser(tax));
            tCounter.incrementAndGet();
            taxonId = tax.getId();

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
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }
        
        @Override
        public void end(Node n) {
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

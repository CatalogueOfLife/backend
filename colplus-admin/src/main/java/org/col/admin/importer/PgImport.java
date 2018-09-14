package org.col.admin.importer;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.ImporterConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.neo.traverse.StartEndHandler;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.col.api.model.*;
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
	private BiMap<Integer, Integer> nameKeys = HashBiMap.create();
  private BiMap<Integer, Integer> referenceKeys = HashBiMap.create();
  private BiMap<Integer, Integer> verbatimKeys = HashBiMap.create();
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger dCounter = new AtomicInteger(0);
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
		insertTaxa();

    checkIfCancelled();
    attach();

    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} references, {} vernaculars and {} distributions",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, rCounter, vCounter, dCounter);
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
  static synchronized void partition(SqlSession session, int datasetKey){
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
  private void attach(){
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
      old.setContactPerson(dataset.getContactPerson());
      old.setDescription(dataset.getDescription());
      old.setHomepage(dataset.getHomepage());
      old.setLicense(dataset.getLicense());
      old.setOrganisation(dataset.getOrganisation());
      old.setReleaseDate(dataset.getReleaseDate());
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

  private void updateVerbatimEntity(VerbatimEntity ent) {
	  if (ent != null && ent.getVerbatimKey() != null) {
	    ent.setVerbatimKey(verbatimKeys.get(ent.getVerbatimKey()));
    }
  }

	private void insertReferences() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        int storeKey = r.getKey();
        r.setDatasetKey(dataset.getKey());
        updateVerbatimEntity(r);
        mapper.create(r);
        rCounter.incrementAndGet();
        // store mapping of key used in the store to the key used in postgres
        referenceKeys.put(storeKey, r.getKey());
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
    // key=postgres name key, value=desired homotypic name key using the temp neo4j node
    Map<Integer, Integer> nameHomoKey = Maps.newHashMap();
    try (final SqlSession session = sessionFactory.openSession(false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.process(Labels.ALL, batchSize, new NodeBatchProcessor() {
        @Override
        public void process(Node n) {
          // we read all names as we also deal with acts for basionyms here
          NeoTaxon t = store.get(n);
          Integer homoKey = null;
          if (t.name.getHomotypicNameKey() != null) {
            if (t.name.getHomotypicNameKey() == n.getId()) {
              // pointer to itself, remove the key as the mapper expects a null in such case
              t.name.setHomotypicNameKey(null);
            } else if (nameKeys.containsKey(t.name.getHomotypicNameKey())) {
              // update homotypic key directly
              t.name.setHomotypicNameKey(nameKeys.get(t.name.getHomotypicNameKey()));
            } else {
              // queue for later updates
              homoKey = t.name.getHomotypicNameKey();
              t.name.setHomotypicNameKey(null);
            }
          }
          // update published in reference keys
          if (t.name.getPublishedInKey() != null) {
            t.name.setPublishedInKey(referenceKeys.get(t.name.getPublishedInKey()));
          }

          t.name.setDatasetKey(dataset.getKey());
          updateVerbatimEntity(t.name);
          nameMapper.create(t.name);
          nCounter.incrementAndGet();
          // keep postgres keys in node id map
          nameKeys.put((int) t.node.getId(), t.name.getKey());

          if (homoKey != null) {
            nameHomoKey.put(t.name.getKey(), homoKey);
          }
        }

        @Override
        public void commitBatch(int counter) {
          session.commit();
          LOG.debug("Inserted {} other names", counter);
        }
      });
      session.commit();

      checkIfCancelled();
      int homoUpdateCounter = 0;
      for (Map.Entry<Integer, Integer> homo : nameHomoKey.entrySet()) {
        nameMapper.updateHomotypicNameKey(dataset.getKey(), homo.getKey(), nameKeys.get(homo.getValue()));
        homoUpdateCounter++;
        if (homoUpdateCounter % batchSize == 0) {
          checkIfCancelled();
          session.commit();
        }
      }
      session.commit();
      LOG.info("Updated homotypic name key of {} names", homoUpdateCounter);
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }

  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertNameRelations() throws InterruptedException {
    for (RelType rt : RelType.values()) {
      if (rt.nomRelType == null) continue;

      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(false)) {
        final NameRelationMapper nameRelationMapper = session.getMapper(NameRelationMapper.class);
        LOG.debug("Inserting all {} relations", rt);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            NameRelation nr = new NameRelation();
            nr.setType(rt.nomRelType);
            nr.setDatasetKey(dataset.getKey());
            nr.setNameKey( nameKeys.get( (int) rel.getStartNodeId()));
            nr.setRelatedNameKey( nameKeys.get( (int) rel.getEndNodeId()));
            nr.setNote( (String) rel.getProperty(NeoProperties.NOTE, null));
            nr.setPublishedInKey( (Integer) rel.getProperty(NeoProperties.REF_KEY, null));
            if (rel.hasProperty(NeoProperties.VERBATIM_KEY)) {
              nr.setVerbatimKey(verbatimKeys.get((Integer)rel.getProperty(NeoProperties.VERBATIM_KEY)));
            }

            nameRelationMapper.create(nr);
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
	 * insert taxa with all the rest
	 */
	private void insertTaxa() throws InterruptedException {
		try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Inserting remaining names and all taxa");
      NameDao nameDao = new NameDao(session);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);
      ReferenceMapper refMapper = session.getMapper(ReferenceMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte synonyms will be visited multiple times, remember their name pg key!
      Long2IntMap proParteNames = new Long2IntOpenHashMap();
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<Integer> parentKeys = new Stack<Integer>();

        @Override
        public void start(Node n) {
          NeoTaxon t = store.get(n);
          // use postgres keys
          t.name.setKey(nameKeys.get((int) n.getId()));
          updateVerbatimEntity(t.synonym);
          updateVerbatimEntity(t.taxon);

          // is this a pro parte synonym that we have processed before already?
          if (proParteNames.containsKey(n.getId())) {
            // now add another synonym relation now that the other accepted exists in pg
            nameDao.addSynonym(dataset.getKey(), proParteNames.get(n.getId()), parentKeys.peek(), t.synonym);
            return;
          }

          // insert accepted taxon or synonym
          int taxonKey;
          if (t.isSynonym()) {
            taxonKey = parentKeys.peek();
            nameDao.addSynonym(dataset.getKey(), t.name.getKey(), taxonKey, t.synonym);

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.setParentKey(parentKeys.peek());
            } else if (!n.hasLabel(Labels.ROOT)) {
              throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + t.name.getScientificName());
            }

            t.taxon.setName(t.name);
            t.taxon.setDatasetKey(dataset.getKey());
            taxonMapper.create(t.taxon);
            tCounter.incrementAndGet();
            taxonKey = t.taxon.getKey();

            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentKeys.push(taxonKey);

            // insert vernacular
            for (VernacularName vn : t.vernacularNames) {
              updateVerbatimEntity(vn);
              updateRefKeys(vn);
              vernacularMapper.create(vn, taxonKey, dataset.getKey());
              vCounter.incrementAndGet();
            }

            // insert distributions
            for (Distribution d : t.distributions) {
              updateVerbatimEntity(d);
              updateRefKeys(d);
              distributionMapper.create(d, taxonKey, dataset.getKey());
              dCounter.incrementAndGet();
            }

            // link bibliography
            for (Integer k : t.bibliography) {
              refMapper.linkToTaxon(dataset.getKey(), taxonKey, referenceKeys.get(k));
            }
          }

          // commit in batches
          if (counter++ % batchSize == 0) {
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }

        /**
         * Updates reference keys from internal store keys to postgres keys
         */
        private void updateRefKeys(Referenced obj) {
          obj.setReferenceKeys(
              obj.getReferenceKeys().stream()
                  .map(referenceKeys::get)
                  .collect(Collectors.toSet())
          );
        }

        @Override
        public void end(Node n) {
          // remove this key from parent list if its an accepted taxon
          if (n.hasLabel(Labels.TAXON)) {
            parentKeys.pop();
          }
        }
      });
      session.commit();
      LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);
		}
	}

}

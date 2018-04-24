package org.col.admin.task.importer;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.ImporterConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.traverse.StartEndHandler;
import org.col.admin.task.importer.neo.traverse.TreeWalker;
import org.col.api.model.*;
import org.col.api.vocab.Origin;
import org.col.db.dao.NameDao;
import org.col.db.mapper.*;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 */
public class PgImport implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);

	private final NeoDb store;
	private final int batchSize;
	private final SqlSessionFactory sessionFactory;
	private final Dataset dataset;
	private Map<Integer, Integer> nameKeys = Maps.newHashMap();
  private Map<Integer, Integer> referenceKeys = Maps.newHashMap();
  private final AtomicInteger verbatimCounter = new AtomicInteger(0);
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

	@Override
	public void run() {
	  truncate();
		insertReferences();
    insertNames();
		insertTaxa();

		updateMetadata();

		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} references, {} vernaculars and {} distributions",
        dataset.getKey(), verbatimCounter,
        nCounter, tCounter, rCounter, vCounter, dCounter);
	}

  private void truncate(){
    try (SqlSession session = sessionFactory.openSession(true)) {
      LOG.info("Remove existing data for dataset {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      mapper.truncateDatasetData(dataset.getKey());
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

	private void insertReferences() {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        int storeKey = r.getKey();
        r.setDatasetKey(dataset.getKey());
        mapper.create(r);
        rCounter.incrementAndGet();
        // store mapping of key used in the store to the key used in postgres
        referenceKeys.put(storeKey, r.getKey());
        if (counter++ % batchSize == 0) {
          session.commit();
          LOG.debug("Inserted {} references", counter);
        }
      }
      session.commit();
      LOG.debug("Inserted all {} references", counter);
    }
	}


  private void insertNames() {
    // first basionyms so we can create relations to them for recombinations
    insertBasionyms();
    insertRecombinations();
    LOG.info("Inserted {} name in total", nCounter.get());
  }

  private void insertBasionyms() {
		// basionyms first
		try (final SqlSession session = sessionFactory.openSession(false)) {
			final NameMapper nameMapper = session.getMapper(NameMapper.class);
			// insert original names first and remember postgres keys for subsequent
			// combinations and taxa
			LOG.info("Inserting basionyms");
			store.process(Labels.BASIONYM, batchSize, new NeoDb.NodeBatchProcessor() {

				@Override
				public void process(Node n) {
					NeoTaxon t = store.get(n);
          createName(nameMapper, t);
				}

				@Override
				public void commitBatch(int counter) {
					session.commit();
					LOG.debug("Inserted {} basionyms", counter);
				}
			});
		}
	}

	private int createName(NameMapper mapper, NeoTaxon t) {
    t.name.setDatasetKey(dataset.getKey());
    t.name.getIssues().addAll(t.issues);
    mapper.create(t.name);
    nCounter.incrementAndGet();
    // keep postgres keys in node id map
    nameKeys.put((int) t.node.getId(), t.name.getKey());
    return t.name.getKey();
  }

  private int createTaxon(TaxonMapper mapper, NeoTaxon t) {
    t.taxon.setDatasetKey(dataset.getKey());
    t.taxon.setName(t.name);
    t.taxon.setIssues(t.issues);
    mapper.create(t.taxon);
    tCounter.incrementAndGet();
    return t.taxon.getKey();
  }

  private void insertRecombinations() {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      NameActMapper nameActMapper = session.getMapper(NameActMapper.class);
      LOG.info("Inserting all other names");
      store.process(Labels.ALL, batchSize, new NeoDb.NodeBatchProcessor() {
        @Override
        public void process(Node n) {
          // we read all names as we also deal with acts for basionyms here
          NeoTaxon t = store.get(n);
          // inserted the name as basionym before?
          if (nameKeys.containsKey((int) n.getId())) {
            // use postgres key
            t.name.setKey(nameKeys.get((int) n.getId()));

          } else {
            // update basionym keys
            if (t.name.getHomotypicNameKey() != null) {
              t.name.setHomotypicNameKey(nameKeys.get(t.name.getHomotypicNameKey()));
            }
            createName(nameMapper, t);
          }

          // update published in reference keys
          if (t.name.getPublishedInKey() != null) {
            t.name.setPublishedInKey(referenceKeys.get(t.name.getPublishedInKey()));
          }

          // insert name acts
          for (NameAct act : t.acts) {
            act.setDatasetKey(dataset.getKey());
            // update to use postgres keys
            act.setNameKey(t.name.getKey());
            if (act.getRelatedNameKey() != null) {
              if (nameKeys.containsKey(act.getRelatedNameKey())) {
                act.setRelatedNameKey(nameKeys.get(act.getRelatedNameKey()));
              } else {
                //TODO: update related name key in case we dont have it yet in the db!!!
                LOG.error("Related name in acts not yet supported! Name={}", t.name.getScientificName());
              }
            }
            nameActMapper.create(act);
          }
        }

        @Override
        public void commitBatch(int counter) {
          session.commit();
          LOG.debug("Inserted {} other names", counter);
        }
      });
    }
  }

	/**
	 * insert taxa with all the rest
	 */
	private void insertTaxa() {
		try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Inserting remaining names and all taxa");
      NameDao nameDao = new NameDao(session);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      VerbatimRecordMapper verbatimMapper = session.getMapper(VerbatimRecordMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte synonyms will be visited multiple times, remember their name pg key!
      Long2IntMap proParteNames = new Long2IntOpenHashMap();
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<Integer> parentKeys = new Stack<Integer>();

        @Override
        public void start(Node n) {
          NeoTaxon t = store.get(n);
          // use postgres name key
          t.name.setKey(nameKeys.get((int) n.getId()));
          // is this a pro parte synonym that we have processed before already?
          if (proParteNames.containsKey(n.getId())) {
            // now add another synonym relation now that the other accepted exists in pg
            nameDao.addSynonym(NameDao.toSynonym(dataset.getKey(), parentKeys.peek(), proParteNames.get(n.getId())));
            return;
          }

          // insert accepted taxon or synonym
          Integer taxonKey;
          if (t.isSynonym()) {
            taxonKey = null;
            nameDao.addSynonym(NameDao.toSynonym(dataset.getKey(), parentKeys.peek(), t.name.getKey()));

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.setParentKey(parentKeys.peek());
            } else if (!n.hasLabel(Labels.ROOT)) {
              throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + t.name.getScientificName());
            }
            taxonKey = createTaxon(taxonMapper, t);
            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentKeys.push(taxonKey);

            // insert vernacular
            for (VernacularName vn : t.vernacularNames) {
              updateRefKeys(vn);
              vernacularMapper.create(vn, taxonKey, dataset.getKey());
              vCounter.incrementAndGet();
            }

            // insert distributions
            for (Distribution d : t.distributions) {
              updateRefKeys(d);
              distributionMapper.create(d, taxonKey, dataset.getKey());
              dCounter.incrementAndGet();
            }
          }

          // insert verbatim rec
          LOG.debug("verbatim {}{} tax={} name={}:{}",
              t.name.getOrigin(),
              t.verbatim==null? "" : " "+t.verbatim.getId(),
              taxonKey,
              t.name.getKey(),
              t.name.canonicalNameComplete()
          );
          if (t.name.getOrigin().equals(Origin.SOURCE)) {
            t.verbatim.setDatasetKey(dataset.getKey());
            verbatimMapper.create(t.verbatim, taxonKey, t.name.getKey(), null);
            verbatimCounter.incrementAndGet();
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

		} catch (Exception e) {
      LOG.error("Fatal error during names and taxa insert for dataset {}", dataset.getKey(), e);
      throw e;
		}
	}

}

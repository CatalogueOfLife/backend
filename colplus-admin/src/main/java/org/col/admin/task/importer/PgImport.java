package org.col.admin.task.importer;

import com.google.common.base.Strings;
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
		this.dataset = store.getDataset().orElse(new Dataset());
		this.dataset.setKey(datasetKey);
		this.store = store;
		this.batchSize = cfg.batchSize;
		this.sessionFactory = sessionFactory;
	}

	@Override
	public void run() {
	  truncate();
		insertReferences();
		insertBasionyms();
		insertTaxaAndNames();

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
		// TODO: update last crawled, modified etc...
		try (SqlSession session = sessionFactory.openSession(false)) {
			LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
			DatasetMapper mapper = session.getMapper(DatasetMapper.class);
			// TODO: merge new dataset with old one...
			Dataset old = mapper.get(dataset.getKey());
			if (dataset.getTitle() != null) {
        old.setTitle(dataset.getTitle());
			}
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

	private void insertBasionyms() {
		// basionyms first
		try (final SqlSession session = sessionFactory.openSession(false)) {
			NameMapper nameMapper = session.getMapper(NameMapper.class);
			// insert original names first and remember postgres keys for subsequent
			// combinations and taxa
			LOG.info("Inserting original names");
			store.process(Labels.BASIONYM, batchSize, new NeoDb.NodeBatchProcessor() {
				@Override
				public void process(Node n) {
					NeoTaxon t = store.get(n);
					t.name.setDatasetKey(dataset.getKey());
					nameMapper.create(t.name);
          nCounter.incrementAndGet();
					// keep basionym name key map
					nameKeys.put((int) t.node.getId(), t.name.getKey());
				}

				@Override
				public void commitBatch(int counter) {
					session.commit();
					LOG.debug("Inserted {} basionyms", counter);
				}
			});
		}
	}

	/**
	 * insert taxa with all the rest
	 */
	private void insertTaxaAndNames() {
		try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Inserting remaining names and all taxa");
      NameMapper nameMapper = session.getMapper(NameMapper.class);
      NameActMapper nameActMapper = session.getMapper(NameActMapper.class);
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
          if (Strings.isNullOrEmpty(t.name.getScientificName())) {
            LOG.error("NULL name should not exist! {}", t);
          }
          // is this a pro parte synonym that we have processed before already?
          if (proParteNames.containsKey(n.getId())) {
            // doublechecking - make sure this is really a synonym
            if (t.synonym != null) {
              // now add another synonym relation now that the other accepted exists in pg
              nameMapper.addSynonym(dataset.getKey(), parentKeys.peek(), proParteNames.get(n.getId()));
            } else {
              LOG.warn("We have seen node {} before, but its not a pro parte synonym!", n.getId());
            }
            return;
          }

          // insert name if not yet inserted (=basionym)
          if (nameKeys.containsKey((int) n.getId())) {
            // this is an original name we have already inserted, use postgres keys
            t.name.setKey(nameKeys.get((int) n.getId()));
            t.name.setDatasetKey(dataset.getKey());
          } else {
            // update basionym keys
            if (t.name.getBasionymKey() != null) {
              t.name.setBasionymKey(nameKeys.get(t.name.getBasionymKey()));
            }
            t.name.setDatasetKey(dataset.getKey());
            nameMapper.create(t.name);
            nCounter.incrementAndGet();
          }

          // insert name acts, e.g. published in
          for (NameAct act : t.acts) {
            act.setDatasetKey(dataset.getKey());
            // update to use postgres keys
            act.setNameKey(t.name.getKey());
            act.setReferenceKey(referenceKeys.get(act.getReferenceKey()));
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

          // insert accepted taxon or synonym
          Integer taxonKey;
          if (t.isSynonym()) {
            taxonKey = null;
            if (t.synonym.accepted.size()>1) {
              proParteNames.put(n.getId(), (int) t.name.getKey());
            }
            nameMapper.addSynonym(dataset.getKey(), parentKeys.peek(), t.name.getKey());

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.setParentKey(parentKeys.peek());
            } else if (!n.hasLabel(Labels.ROOT)) {
              throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + t.name.getScientificName());
            }
            t.taxon.setDatasetKey(dataset.getKey());
            t.taxon.setName(t.name);
            taxonMapper.create(t.taxon);
            tCounter.incrementAndGet();
            taxonKey = t.taxon.getKey();
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
          if (t.verbatim != null) {
            t.verbatim.setDatasetKey(dataset.getKey());
            verbatimMapper.create(t.verbatim, taxonKey, t.name.getKey());
            verbatimCounter.incrementAndGet();

          } else if (t.name.getOrigin().equals(Origin.SOURCE)) {
            LOG.warn("No verbatim record for {}", t.name);
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

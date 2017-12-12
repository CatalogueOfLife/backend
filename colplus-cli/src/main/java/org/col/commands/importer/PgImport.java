package org.col.commands.importer;

import com.google.common.collect.Maps;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.api.Distribution;
import org.col.api.VernacularName;
import org.col.api.vocab.Origin;
import org.col.commands.config.ImporterConfig;
import org.col.commands.importer.neo.NeoDb;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.commands.importer.neo.traverse.StartEndHandler;
import org.col.commands.importer.neo.traverse.TreeWalker;
import org.col.db.mapper.*;
import org.gbif.dwc.terms.DwcTerm;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Stack;

/**
 *
 */
public class PgImport implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);

	private final NormalizerStore store;
	private final int batchSize;
	private final SqlSessionFactory sessionFactory;
	private final Dataset dataset;
	private Map<Integer, Integer> originalNameKeys = Maps.newHashMap();

	public PgImport(int datasetKey, NormalizerStore store, SqlSessionFactory sessionFactory,
                  ImporterConfig cfg) {
		this.dataset = store.getDataset();
		this.dataset.setKey(datasetKey);
		this.store = store;
		this.batchSize = 10000;
		this.sessionFactory = sessionFactory;
	}

	@Override
	public void run() {
	  truncate();
		insertReferences();
		insertBasionyms();
		insertTaxaAndNames();

		updateMetadata();
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
		LOG.warn("Inserting references not yet implemented");
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
					// keep basionym name key map
					originalNameKeys.put((int) t.node.getId(), t.name.getKey());
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
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      VerbatimRecordMapper verbatimMapper = session.getMapper(VerbatimRecordMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<Integer> parentKeys = new Stack<Integer>();

        @Override
        public void start(Node n) {
          NeoTaxon t = store.get(n);
          // insert name if not yet inserted (=basionym)
          if (originalNameKeys.containsKey((int) n.getId())) {
            // this is an original name we have already inserted, use postgres keys
            t.name.setKey(originalNameKeys.get((int) n.getId()));
            t.name.setDatasetKey(dataset.getKey());
          } else {
            // update basionym keys
            if (t.name.getBasionymKey() != null) {
              t.name.setBasionymKey(originalNameKeys.get(t.name.getBasionymKey()));
            }
            t.name.setDatasetKey(dataset.getKey());
            if (t.name.getType() == null) {
              int x = 87;
            }
            nameMapper.create(t.name);
          }

          // insert accepted taxon or synonym
          Integer taxonKey;
          if (t.isSynonym()) {
            // TODO: insert synonym relations!
            taxonKey = null;

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.setParentKey(parentKeys.peek());
            }
            t.taxon.setDatasetKey(dataset.getKey());
            t.taxon.setName(t.name);
            taxonMapper.create(t.taxon);
            taxonKey = t.taxon.getKey();
            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentKeys.push(taxonKey);

            // insert vernacular
            for (VernacularName vn : t.vernacularNames) {
              vernacularMapper.create(vn, taxonKey, dataset.getKey());
            }

            // insert distributions
            for (Distribution d : t.distributions) {
              distributionMapper.create(d, taxonKey, dataset.getKey());
            }
          }

          // insert verbatim rec
          if (t.verbatim != null) {
            t.verbatim.setDataset(dataset);
            LOG.info("id{} tid:{} nid:{} name:{}", t.verbatim.getId(), taxonKey, t.name.getKey(), t.verbatim.getCoreTerm(DwcTerm.scientificName));
            verbatimMapper.create(t.verbatim, taxonKey, t.name.getKey());

          } else if (t.name.getOrigin().equals(Origin.SOURCE)) {
            LOG.warn("No verbatim record for {}", t.name);
          }

          // commit in batches
          if (counter++ % batchSize == 0) {
            session.commit();
            LOG.debug("Inserted {} names and taxa", counter);
          }
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
      LOG.debug("Inserted all names and taxa");

		} catch (Exception e) {
      LOG.error("Fatal error during names and taxa insert", e);
      throw e;

		}
	}

}

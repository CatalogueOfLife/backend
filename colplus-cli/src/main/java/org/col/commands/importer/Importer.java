package org.col.commands.importer;

import com.google.common.collect.Maps;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.commands.config.ImporterConfig;
import org.col.commands.importer.neo.NeoDb;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.commands.importer.neo.traverse.StartEndHandler;
import org.col.commands.importer.neo.traverse.TreeWalker;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.TaxonMapper;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Stack;

/**
 *
 */
public class Importer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Importer.class);

  private final NormalizerStore store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final Dataset dataset;
  private Map<Integer, Integer> originalNameKeys = Maps.newHashMap();

  public Importer(int datasetKey, NormalizerStore store, SqlSessionFactory sessionFactory, ImporterConfig cfg) {
    this.dataset = store.getDataset();
    this.dataset.setKey(datasetKey);
    this.store = store;
    this.batchSize = 10000;
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void run() {
    insertReferences();
    insertBasionyms();
    insertTaxaAndNames();

    updateMetadata();
  }

  private void updateMetadata() {
    //TODO: update last crawled, modified etc...
    try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      // TODO: merge new dataset with old one...
      Dataset old = mapper.get(dataset.getKey());
      if (dataset.getTitle() == null) {
        dataset.setTitle(old.getTitle());
      }
      mapper.update(dataset);
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
      // insert original names first and remember postgres keys for subsequent combinations and taxa
      LOG.info("Inserting original names");
      store.process(Labels.BASIONYM, batchSize, new NeoDb.NodeBatchProcessor() {
        @Override
        public void process(Node n) {
          NeoTaxon t = store.get(n);
          t.name.setDataset(dataset);
          nameMapper.create(t.name);
          // keep basionym name key map
          originalNameKeys.put((int)t.node.getId(), t.name.getKey());
        }

        @Override
        public boolean commitBatch(int counter) {
          session.commit();
          LOG.debug("Inserted {} basionyms", counter);
          return true;
        }

        @Override
        public boolean finalBatch(int counter) {
          session.commit();
          LOG.info("Inserted {} basionyms", counter);
          return true;
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
      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<Integer> parentKeys = new Stack<Integer>();

        @Override
        public void start(Node n) {
          NeoTaxon t = store.get(n);
          // insert name if not yet inserted (=basionym)
          if (originalNameKeys.containsKey((int)n.getId())) {
            // this is an original name we have already inserted, use postgres keys
            t.name.setKey(originalNameKeys.get((int)n.getId()));
            t.name.setDataset(dataset);
          } else {
            // update basionym keys
            if (t.name.getBasionym() != null) {
              t.name.getBasionym().setKey(originalNameKeys.get(t.name.getBasionym().getKey()));
            }
            t.name.setDataset(dataset);
            nameMapper.create(t.name);
          }


          // insert accepted taxon or synonym
          if (t.isSynonym()) {
            // TODO: insert synonym relations!

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.getParent().setKey(parentKeys.peek());
            }
            t.taxon.setDataset(dataset);
            t.taxon.setName(t.name);
            taxonMapper.create(t.taxon);
            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentKeys.push(t.taxon.getKey());
          }

          //TODO: insert related infos

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
    }
  }
}

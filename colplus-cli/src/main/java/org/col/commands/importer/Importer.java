package org.col.commands.importer;

import com.google.common.collect.Maps;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.commands.config.ImporterConfig;
import org.col.commands.importer.neo.NeoDb;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.TaxonMapper;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 *
 */
public class Importer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Importer.class);

  private final NormalizerStore store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final Dataset dataset;
  private int counter = 0;

  public Importer(int datasetKey, NormalizerStore store, SqlSessionFactory sessionFactory, ImporterConfig cfg) {
    this.dataset = store.getDataset();
    this.dataset.setKey(datasetKey);
    this.store = store;
    this.batchSize = 10000;
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void run() {
    insertData();
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

  private void insertData() {
    try (SqlSession session = sessionFactory.openSession(false)) {

      // TODO: insert references

      NameMapper nameMapper = session.getMapper(NameMapper.class);
      // insert original names first and remember postgres keys for subsequent combinations and taxa
      LOG.info("Inserting original names");
      Map<Long, Integer> originalNameKeys = Maps.newHashMap();
//      store.originalNames().forEach(t -> {
//        t.name.setDataset(dataset);
//        nameMapper.create(t.name);
//        originalNameKeys.put(t.node.getId(), t.name.getKey());
//        countAndCommit(session);
//      });
      session.commit();
      LOG.info("Inserted {} original names", counter);

      // insert taxa with all the rest
      LOG.info("Inserting remaining names and all taxa");
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      store.processAll(1000, new NeoDb.NodeBatchProcessor() {
        @Override
        public void process(Node n) {
          NeoTaxon t = store.get(n);
          if (originalNameKeys.containsKey(t.node.getId())) {
            // this is an original name we have already inserted!
            t.name.setKey(originalNameKeys.get(t.node.getId()));
            t.name.setDataset(dataset);
          } else {
            nameMapper.create(t.name);
            countAndCommit(session);
          }
          // TODO: insert synonym relations!
          if (t.taxon != null) {
            t.taxon.setDataset(dataset);
            t.taxon.setName(t.name);
            taxonMapper.create(t.taxon);
            countAndCommit(session);

            //TODO: insert related infos
          }
        }

        @Override
        public boolean commitBatch(int counter) {
          return false;
        }
      });
      session.commit();
    }
  }

  private void countAndCommit(SqlSession session) {
    counter++;
    if (counter % batchSize == 0) {
      session.commit();
      LOG.info("Inserted {} records", counter);
    }
  }
}

package org.col.dao;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.NameUsageWrapperMapper;
import org.col.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameUsageProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageProcessor.class);

  private final SqlSessionFactory factory;
  private final int datasetKey;
  
  public final static Comparator<String> PG_C_COLLATION = (left, right) -> {
    Collator collator = Collator.getInstance(Locale.ROOT);
    collator.setStrength(Collator.PRIMARY);
    return collator.compare(left.replaceAll("\\p{Punct}", ""), right.replaceAll("\\p{Punct}", ""));
  };
  
  public NameUsageProcessor(SqlSessionFactory factory, int datasetKey) {
    this.factory = factory;
    this.datasetKey = datasetKey;
  }
  
  /**
   * to avoid large in memory or even external pg temp files dissect the problem into the following steps:
   *
   *  1. list all root taxa, then iterate through each of them separately with
   *  2. call the SimpleName tree processor that iterates over all usages in taxonomic order and
   *  3. a) built the flat classification in java, call getWrapperDetail for each id and stream results
   *     b) keep entire SimpleName tree in memory (needs how much of java heap then for large animalia datasets???) with a new parent (SimpleName) object reference.
   *        Load wrapper details from db in large batches of 1-10.000 by using usageID ranges instead of random id ordering.
   *        ID Ranges must make sure the java sorting is exactly the same as the postgres (C-collation) sorting.
   *
   * @param handler
   */
  public void processDataset(ResultHandler<NameUsageWrapper> handler) {
    List<String> rootIds;
    try (SqlSession s = factory.openSession(true)) {
      rootIds = s.getMapper(TaxonMapper.class).listRootIds(datasetKey);
    }
    LOG.debug("Process dataset {} with {} root taxa", datasetKey, rootIds.size());
    for (String id : rootIds) {
      processTree(datasetKey, id, handler);
    }
  }
  
  private void processTree(int datasetKey, String id, ResultHandler<NameUsageWrapper> handler) {
    LOG.debug("Process dataset {} tree with root taxon {}", datasetKey, id);
    try (SqlSession s = factory.openSession(true)) {
      NameUsageWrapperMapper nuwm = s.getMapper(NameUsageWrapperMapper.class);
      nuwm.processTreeUsages(datasetKey, id, handler);
    }
  }
  
}

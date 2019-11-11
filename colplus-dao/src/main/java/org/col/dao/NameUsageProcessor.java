package org.col.dao;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.SimpleNameClassification;
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
   *  2. call the SimpleName tree processor that iterates over all usages in taxonomic order and generates the classification
   *  3. call getWrapperDetail for each id to get the full object without classification and stream results
   *
   *  Alternatively one could hold the entire classification in memory (10 million only need ~500MB)
   *  and select the wrapper objects in batch id ranges.
   *  But that would require traversing the entire tree so we are sure we have processed all ids, not just children of the root node.
   *  ID ranges must make sure the java sorting is exactly the same as the postgres (C-collation) sorting.
   *
   * @param consumer
   */
  public void processDataset(Consumer<NameUsageWrapper> consumer) {
    List<String> rootIds;
    try (SqlSession s = factory.openSession(true)) {
      rootIds = s.getMapper(TaxonMapper.class).listRootIds(datasetKey);
    }
    LOG.info("Process dataset {} with {} root taxa", datasetKey, rootIds.size());
    for (String id : rootIds) {
      processTree(datasetKey, id, consumer);
    }
  }
  
  private void processTree(int datasetKey, String id, Consumer<NameUsageWrapper> consumer) {
    LOG.debug("Process dataset {} tree with root taxon {}", datasetKey, id);
    try (SqlSession s = factory.openSession(true)) {
      final NameUsageWrapperMapper nuwm = s.getMapper(NameUsageWrapperMapper.class);
      SNCHandler handler = new SNCHandler(consumer, nuwm, datasetKey);
      nuwm.processTree(datasetKey, id, handler);
    }
  }
  
  static class SNCHandler implements ResultHandler<SimpleNameClassification> {
    final Consumer<NameUsageWrapper> handler;
    final NameUsageWrapperMapper nuwm;
    final int datasetKey;

    SNCHandler(Consumer<NameUsageWrapper> handler, NameUsageWrapperMapper nuwm, int datasetKey) {
      this.handler = handler;
      this.nuwm = nuwm;
      this.datasetKey = datasetKey;
    }

    @Override
    public void handleResult(ResultContext<? extends SimpleNameClassification> ctx) {
      SimpleNameClassification cl = ctx.getResultObject();
      NameUsageWrapper obj = nuwm.getWithoutClassification(datasetKey, cl.getId());
      obj.setClassification(cl.getClassification());
      handler.accept(obj);
    }
  }
  
}

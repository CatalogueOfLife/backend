package life.catalogue.dao;

import com.google.common.collect.Iterables;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.db.mapper.TaxonMapper;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NameUsageProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageProcessor.class);

  private final SqlSessionFactory factory;
  
  public NameUsageProcessor(SqlSessionFactory factory) {
    this.factory = factory;
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
  public void processDataset(int datasetKey, Consumer<NameUsageWrapper> consumer) {
    List<String> rootIds;
    try (SqlSession s = factory.openSession(true)) {
      rootIds = s.getMapper(TaxonMapper.class).listRootIds(datasetKey);
    }
    LOG.info("Process dataset {} with {} root taxa", datasetKey, rootIds.size());
    for (String id : rootIds) {
      processTree(datasetKey, null, id, consumer);
    }
  }
  
  /**
   * Process all catalogue usages from a given sector
   * @param s the sector to process
   */
  public void processSector(Sector s, Consumer<NameUsageWrapper> consumer) {
    if (s.getTarget().getId() == null) {
      LOG.warn("Sector {} with target {} is broken. Do not process", s.getKey(), s.getTarget());
      return;
    }
    LOG.info("Process sector{} of dataset {} with target {}", s.getKey(), s.getDatasetKey(), s.getTarget());
    processTree(s.getDatasetKey(), s.getId(), s.getTarget().getId(), consumer);
  }
  
  private void processTree(int datasetKey, @Nullable Integer sectorKey, String id, Consumer<NameUsageWrapper> consumer) {
    LOG.debug("Process dataset {} tree with root taxon {}", datasetKey, id);
    try (SqlSession s = factory.openSession()) {
      final NameUsageWrapperMapper nuwm = s.getMapper(NameUsageWrapperMapper.class);
      Cursor<SimpleNameClassification> c = nuwm.processTree(datasetKey, sectorKey, id);
      for (List<SimpleNameClassification> cls : Iterables.partition(c, 50)) {
        Map<String, NameUsageWrapper> objs = nuwm.getSomeWithoutClassification(datasetKey, cls.stream()
            .map(SimpleNameClassification::getId)
            .collect(Collectors.toList())).stream()
            .collect(Collectors.toMap(o-> o.getUsage().getId(), Function.identity()));
        for (SimpleNameClassification cl : cls) {
          NameUsageWrapper obj = objs.get(cl.getId());
          obj.setClassification(cl.getClassification());
          consumer.accept(obj);
        }
      }
    }
  }

}

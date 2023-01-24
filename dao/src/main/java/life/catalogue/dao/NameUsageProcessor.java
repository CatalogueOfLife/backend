package life.catalogue.dao;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.db.mapper.*;

import java.util.*;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    LOG.info("Process dataset {}", datasetKey);
    processTree(datasetKey, null, consumer);
  }
  
  /**
   * Process all catalogue usages from a given sector
   * @param sectorKey the sector to process
   */
  public void processSector(DSID<Integer> sectorKey, Consumer<NameUsageWrapper> consumer) {
    LOG.info("Process sector{} of dataset {}", sectorKey.getKey(), sectorKey.getDatasetKey());
    processTree(sectorKey.getDatasetKey(), sectorKey.getId(), consumer);
  }
  
  private void processTree(int datasetKey, @Nullable Integer sectorKey, Consumer<NameUsageWrapper> consumer) {
    try (SqlSession session = factory.openSession()) {
      final NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
      final TaxonMapper tm = session.getMapper(TaxonMapper.class);
      final var sm = session.getMapper(SectorMapper.class);

      // reusable dsids for this dataset
      final DSID<String> taxKey = DSID.of(datasetKey, null);
      final DSID<Integer> sKey = DSID.of(datasetKey, null);
      // we exclude some rather static info from our already massive joins and set them manually in code:
      final UUID publisher = session.getMapper(DatasetMapper.class).getPublisherKey(datasetKey);
      // we prefetch sectorKey to the sectors subject dataset key depending whether we process a sector or entire dataset
      final Map<Integer, Integer> sectorDatasetKeys = new HashMap<>();
      if (sectorKey != null) {
        sectorDatasetKeys.put(sectorKey, sm.get(sKey.id(sectorKey)).getSubjectDatasetKey());
      } else {
        sm.listByDataset(datasetKey, null).forEach(s -> sectorDatasetKeys.put(s.getId(), s.getSubjectDatasetKey()));
      }

      // build temporary table collecting issues from all usage related tables
      // we do this in a separate step to not overload postgres with gigantic joins later on
      session.getMapper(VerbatimRecordMapper.class).createTmpIssuesTable(datasetKey, sectorKey);

      // reusable lookup for classifications and synonyms
      final Map<String, Taxon> taxa = new HashMap<>();
      int counter = 0;
      int loadCounter = 0;
      for (var nuw : nuwm.processWithoutClassification(datasetKey, sectorKey)) {
        // set preloaded infos excluded in sql results as they are very repetitive
        nuw.setPublisherKey(publisher);
        if (nuw.getUsage().getName().getSectorKey() != null) {
          nuw.setSectorDatasetKey(sectorDatasetKeys.get(nuw.getUsage().getName().getSectorKey()));
        }

        if (nuw.getUsage().isTaxon()) {
          Taxon t = (Taxon) nuw.getUsage();
          taxa.put(t.getId(), t);
        }

        if (nuw.getUsage().isSynonym()) {
          Synonym syn = (Synonym) nuw.getUsage();
          // we list all accepted first, so the key must exist
          syn.setAccepted(Preconditions.checkNotNull(taxa.get(syn.getParentId()), "accepted name for synonym "+ syn +" missing"));
        }

        List<SimpleName> classification = new ArrayList<>();
        if (!nuw.getUsage().isBareName()) {
          NameUsageBase curr = (NameUsageBase) nuw.getUsage();
          classification.add(new SimpleName(curr));
          while (curr.getParentId() != null) {
            if (taxa.containsKey(curr.getParentId())) {
              curr = taxa.get(curr.getParentId());
            } else {
              // fetch taxon before the main cursor hits it - ranks are not always properly ordered according to the tree
              Taxon t = tm.get(taxKey.id(curr.getParentId()));
              loadCounter++;
              taxa.put(curr.getId(), t);
              curr = t;
            }
            classification.add(new SimpleName(curr));
          }
        }
        Collections.reverse(classification);
        nuw.setClassification(classification);

        consumer.accept(nuw);
        if (counter++ % 1000 == 0) {
          LOG.debug("Processed {} usages of dataset {}; preloaded taxa={}; cached taxa={}", counter, datasetKey, loadCounter, taxa.size());
        }
      }
    }
  }
}

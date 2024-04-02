package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.ObjectCache;
import life.catalogue.cache.ObjectCacheMapDB;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import life.catalogue.matching.TaxGroupAnalyzer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not thread safe !
 * A usage processor that accepts various filters and feeds a consumer with the full classification of the usage.
 * This is done by caching of all accepted taxa in a file persisted mapdb.
 *
 * Usages are ordered by status, with synonyms coming first before any taxa.
 */
public class NameUsageProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageProcessor.class);
  private static final int LOG_INTERVAL = 5000;

  private final SqlSessionFactory factory;
  private final TaxGroupAnalyzer groupAnalyzer = new TaxGroupAnalyzer();
  private int loadCounter = 0;
  private final File tmpDir;

  public NameUsageProcessor(SqlSessionFactory factory, File tmpDir) {
    this.factory = factory;
    this.tmpDir = tmpDir;
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

  /**
   * Container for indexed sector data
   */
  public static class SectorProps {
    public int datasetKey; // sector subject
    public UUID publisherKey; // sector subject
    public Sector.Mode mode;

    public SectorProps(Sector s, DatasetMapper dm) {
      this.mode = s.getMode();
      this.datasetKey = s.getSubjectDatasetKey();
      this.publisherKey = dm.getPublisherKey(datasetKey);
    }
  }

  public static void addUsageSectorData(NameUsageWrapper nuw, @Nullable SectorProps sectorProps){
    if (sectorProps != null) {
      nuw.setSectorDatasetKey(sectorProps.datasetKey);
      nuw.setSectorPublisherKey(sectorProps.publisherKey);
      nuw.getUsage().setSectorMode(sectorProps.mode);
    }
  }
  public static void addNameSectorData(NameUsageWrapper nuw, @Nullable SectorProps sectorProps){
    if (sectorProps != null) {
      nuw.getUsage().getName().setSectorMode(sectorProps.mode);
    }
  }
  private void processTree(int datasetKey, @Nullable Integer sectorKey, Consumer<NameUsageWrapper> consumer) {
    try (SqlSession session = factory.openSession()) {
      final NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
      final NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      final var sm = session.getMapper(SectorMapper.class);
      final var dm = session.getMapper(DatasetMapper.class);
      final CacheLoader loader = new CacheLoader.Mybatis(session, false);

      // reusable dsids for this dataset
      final DSID<String> uKey = DSID.of(datasetKey, null);
      final DSID<Integer> sKey = DSID.of(datasetKey, null);
      // we exclude some rather static info from our already massive joins and set them manually in code:
      final UUID publisher = session.getMapper(DatasetMapper.class).getPublisherKey(datasetKey);
      // we prefetch sectorKey to the sectors subject dataset key depending whether we process a sector or entire dataset
      final Map<Integer, SectorProps> sectors = new HashMap<>();
      if (sectorKey != null) {
        sectors.put(sectorKey, new SectorProps(sm.get(sKey.id(sectorKey)), dm));
      } else {
        sm.listByDataset(datasetKey, null).forEach(s -> sectors.put(s.getId(), new SectorProps(s, dm)));
      }

      // build temporary table collecting issues from all usage related tables
      // we do this in a separate step to not overload postgres with gigantic joins later on
      var vm = session.getMapper(VerbatimRecordMapper.class);
      vm.createTmpIssuesTable(datasetKey, sectorKey);
      vm.createTmpVSourcesTable(datasetKey, sectorKey);

      try (ObjectCache<NameUsageWrapper> taxa = buildObjCache();
           UsageCache usageCache = buildUsageCache()
      ) {
        final AtomicInteger counter = new AtomicInteger(0);
        // processing first returns all taxa before any synonym is returned - cache these and process them at the end
        PgUtils.consume(() -> nuwm.processWithoutClassification(datasetKey, sectorKey), nuw -> {
          // set preloaded infos excluded in sql results as they are very repetitive
          nuw.setPublisherKey(publisher);
          if (nuw.getUsage().getSectorKey() != null) {
            addUsageSectorData(nuw, sectors.get(nuw.getUsage().getSectorKey()));
          }
          if (nuw.getUsage().getName() != null && nuw.getUsage().getName().getSectorKey() != null) {
            addNameSectorData(nuw, sectors.get(nuw.getUsage().getName().getSectorKey()));
          }

          if (nuw.getUsage().isTaxon()) {
            taxa.put(nuw);
            // dont do anything else here now - we load all taxa first and process them later to build up the classification
            if (counter.incrementAndGet() % LOG_INTERVAL == 0) {
              LOG.debug("Cached {} taxa of dataset {}", counter, datasetKey);
            }

          } else {
            // synonym or bare name
            if (nuw.getUsage().isSynonym()) {
              // when we see a synonym all taxa must already been loaded
              Synonym syn = (Synonym) nuw.getUsage();
              if (syn.getParentId()==null) {
                // major data inconsistency - cant work with this one!
                LOG.warn("Synonym {} without parentID found {}", syn.getId(), syn.getLabel());
                return;
              }
              // we list all accepted first, so the key must exist UNLESS we have a merged synonym pointing to an accepted name outside of its sector!
              NameUsage acc;
              if (!taxa.contains(syn.getParentId())) {
                // load it
                LOG.debug("Load missing usage {} of dataset {}", syn.getParentId(), datasetKey);
                acc = num.get(uKey.id(syn.getParentId()));
                if (acc == null) {
                  LOG.error("Bad data. Accepted parent usage {} of dataset {} not found for synonym {} {}!", syn.getParentId(), datasetKey, syn.getId(), syn.getLabel());
                  throw new IllegalStateException(String.format("Accepted parent usage %s of dataset %s not found for synonym %s %s!", syn.getParentId(), datasetKey, syn.getId(), syn.getLabel()));
                } else {
                  taxa.put(new NameUsageWrapper(acc));
                }
              } else {
                acc = taxa.get(syn.getParentId()).getUsage();
              }
              syn.setAccepted((Taxon)acc);
              addClassification(nuw, taxa, usageCache, loader);
            }
            nuw.setGroup(groupAnalyzer.analyze(nuw.getUsage().toSimpleNameLink(), nuw.getClassification()));
            consumer.accept(nuw);
            if (counter.incrementAndGet() % LOG_INTERVAL == 0) {
              LOG.debug("Processed {} usages of dataset {}; loaded taxa={}", counter, datasetKey, loadCounter);
            }
          }
        });

        // now lets do the cached taxa
        LOG.info("Process {} taxa of dataset {}; loaded taxa={}", taxa.size(), datasetKey, loadCounter);
        for (var nuw : taxa) {
          addClassification(nuw, taxa, usageCache, loader);
          nuw.setGroup(groupAnalyzer.analyze(nuw.getUsage().toSimpleNameLink(), nuw.getClassification()));
          consumer.accept(nuw);
          if (counter.incrementAndGet() % LOG_INTERVAL == 0) {
            LOG.debug("Processed {} usages of dataset {}; loaded taxa={}", counter, datasetKey, loadCounter);
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void addClassification(NameUsageWrapper nuw, ObjectCache<NameUsageWrapper> taxa, UsageCache usageCache, CacheLoader loader) {
    List<SimpleName> classification = new ArrayList<>();
    DSID<String> uKey = null;
    if (!nuw.getUsage().isBareName()) {
      SimpleName curr = new SimpleName((NameUsageBase) nuw.getUsage());
      classification.add(curr);
      while (curr != null && curr.getParent() != null) {
        if (taxa.contains(curr.getParent())) {
          curr = new SimpleName((NameUsageBase) taxa.get(curr.getParent()).getUsage());
        } else {
          // need to fetch usage which lies outside the scope of this processor, e.g. a merge sector with parents outside of the sector
          if (uKey == null) {
            uKey = DSID.root(nuw.getUsage().getDatasetKey());
          }
          curr = usageCache.getOrLoad(uKey.id(curr.getParent()), loader);
          loadCounter++;
        }
        if (curr != null) {
          classification.add(curr);
        }
      }
    }
    Collections.reverse(classification);
    nuw.setClassification(classification);
  }

  private ObjectCache<NameUsageWrapper> buildObjCache() throws IOException {
    return new ObjectCacheMapDB<>(NameUsageWrapper.class, new File(tmpDir, UUID.randomUUID().toString()), new ApiKryoPool(8));
  }
  private UsageCache buildUsageCache() throws Exception {
    var cache = UsageCache.mapDB(new File(tmpDir, UUID.randomUUID().toString()), true, 8);
    cache.start();
    return cache;
  }
}

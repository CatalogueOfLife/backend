package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.common.collection.CountMap;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMetricsMapper;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class TaxonMetricsBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonMetricsBuilder.class);
  private final ClassificationTracker clTracker;
  private final DSID<String> key;
  private int nestedSetIdx = 1;
  private final Stack<TaxonMetrics> parentsM = new Stack<>();
  private final TaxonMetricsMapper metricsMapper;
  private final SectorMapper sectorMapper;
  private final LoadingCache<Integer, Integer> sourceBySector = Caffeine.newBuilder()
    .maximumSize(1000)
    .build(this::lookupSource);

  public interface ClassificationTracker {
    int depth();
    List<SimpleName> classification();
  }
  public static ClassificationTracker tracker(final Stack<SimpleName> stack) {
    return new ClassificationTracker() {
      @Override
      public int depth() {
        return stack.size();
      }

      @Override
      public List<SimpleName> classification() {
        return stack;
      }
    };
  }
  public static ClassificationTracker tracker(final ParentStack<? extends LinneanNameUsage> stack) {
    return new ClassificationTracker() {
      @Override
      public int depth() {
        return stack.depth();
      }

      @Override
      public List<SimpleName> classification() {
        return stack.getParents(false).stream()
          .map(SimpleName::new)
          .collect(Collectors.toList());
      }
    };
  }

  public static void rebuildMetrics(SqlSessionFactory factory, int datasetKey) {
    LOG.info("Remove existing taxon metrics for dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(TaxonMetricsMapper.class).deleteByDataset(datasetKey);
    }

    LOG.info("Starting to rebuild all taxon metrics for dataset {}", datasetKey);
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession sessionRO = factory.openSession(true);
         SqlSession session = factory.openSession(false)
    ) {
      // add metrics generator to tree traversal
      final var stack = new ParentStack<LinneanNameUsage>();
      TaxonMetricsBuilder mb = new TaxonMetricsBuilder(tracker(stack), datasetKey, session);
      stack.addHandler(new ParentStack.StackHandler<>() {
        @Override
        public void start(LinneanNameUsage n) {
          mb.start(n.getId());
        }
        @Override
        public void end(ParentStack.SNC<LinneanNameUsage> snc) {
          mb.end(snc.usage, snc.usage.getSectorKey(), snc.usage.getExtinct());
        }
      });
      // traverse accepted tree
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(datasetKey);
      params.setSynonyms(false);

      var num = sessionRO.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), u -> {
        stack.push(u);
        if (counter.incrementAndGet() % 5000 == 0) {
          session.commit();
        }
      });
      stack.flush();
      session.commit();
    }
    LOG.info("Finished rebuilding {} taxon metrics for dataset {}", counter, datasetKey);
  }

  public TaxonMetricsBuilder(ClassificationTracker classificationTracker, int datasetKey, SqlSession session) {
    this.clTracker = classificationTracker;
    this.key = DSID.root(datasetKey);
    this.metricsMapper = session.getMapper(TaxonMetricsMapper.class);
    this.sectorMapper = session.getMapper(SectorMapper.class);
  }

  public void start(String taxonID) {
    var tm = TaxonMetrics.create(key.id(taxonID));
    tm.setLft(nestedSetIdx++);
    parentsM.add(tm);
  }

  public TaxonMetrics end(NameUsageCore sn, Integer sectorKey, Boolean extinct) {
    // update & store metrics
    final var m = parentsM.pop();
    m.setRgt(nestedSetIdx++);
    m.setId(sn.getId());
    m.setDepth(clTracker.depth());
    m.setMaxDepthIfHigher(clTracker.depth());
    m.setClassification(clTracker.classification());
    metricsMapper.create(m);
    // now add all metrics from this child taxon to it's parent - that's how we aggregate
    if (!parentsM.isEmpty()) {
      // first add this taxon to the metrics to be added to the parent - otherwise we never add anything
      m.incTaxonCount();
      if (sn.getRank() == Rank.SPECIES) {
        Integer sourceKey = sectorSourceDatasetKey(sectorKey);
        if (sourceKey != null) {
          m.getSourceDatasetKeys().add(sourceKey);
          ((CountMap<Integer>)m.getSpeciesBySourceCount()).inc(sourceKey);
        }
      }
      ((CountMap<Rank>)m.getTaxaByRankCount()).inc(sn.getRank());
      // now aggregate child metrics with parent
      var p = parentsM.peek();
      p.add(m);
      p.incChildCount();
      if (extinct == null || !extinct) {
        p.incChildExtantCount();
      }
    }
    return m;
  }

  private Integer lookupSource(Integer sectorKey) {
    return sectorMapper.get(DSID.of(key.getDatasetKey(), sectorKey)).getSubjectDatasetKey();
  }

  private Integer sectorSourceDatasetKey(@Nullable Integer sectorKey) {
    return sectorKey == null ? null : sourceBySector.get(sectorKey);
  }
}

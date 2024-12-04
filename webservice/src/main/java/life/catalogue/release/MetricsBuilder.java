package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TaxonMetrics;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.date.DateUtils;
import life.catalogue.dao.MetricsDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMetricsMapper;

import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsBuilder implements ParentStack.StackHandler<TreeCleanerAndValidator.XLinneanNameUsage> {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsBuilder.class);
  private final ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> stack;
  private final DSID<String> key;
  private final Stack<TaxonMetrics> parentsM = new Stack<>();
  private final TaxonMetricsMapper metricsMapper;
  private final SectorMapper sectorMapper;
  private final LoadingCache<Integer, Integer> sourceBySector = Caffeine.newBuilder()
    .maximumSize(1000)
    .build(this::lookupSource);

  public static void rebuildMetrics(SqlSessionFactory factory, int datasetKey) {
    LOG.info("Remove existing taxon metrics for dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(TaxonMetricsMapper.class).deleteByDataset(datasetKey);
    }

    LOG.info("Starting to rebuild all taxon metrics for dataset {}", datasetKey);
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession(false)) {
      // add metrics generator to tree traversal
      final var stack = new ParentStack<TreeCleanerAndValidator.XLinneanNameUsage>();
      MetricsBuilder mb = new MetricsBuilder(stack, datasetKey, session);
      stack.addHandler(mb);
      // traverse accepted tree
      var num = session.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(datasetKey);
      params.setSynonyms(false);

      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), u -> {
        var xu = new TreeCleanerAndValidator.XLinneanNameUsage(u);
        stack.push(xu);
        if (counter.incrementAndGet() % 5000 == 0) {
          session.commit();
        }
      });
      stack.flush();
      session.commit();
    }
    LOG.info("Finished rebuilding {} taxon metrics for dataset {}", counter, datasetKey);
  }

  public MetricsBuilder(ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> stack, int datasetKey, SqlSession session) {
    this.stack = stack;
    this.key = DSID.root(datasetKey);
    this.metricsMapper = session.getMapper(TaxonMetricsMapper.class);
    this.sectorMapper = session.getMapper(SectorMapper.class);
  }

  private Integer lookupSource(Integer sectorKey) {
    return sectorMapper.get(DSID.of(key.getDatasetKey(), sectorKey)).getSubjectDatasetKey();
  }

  @Override
  public void start(TreeCleanerAndValidator.XLinneanNameUsage n) {
    parentsM.add(TaxonMetrics.create(key.id(n.getId())));
  }

  private static List<SimpleName> toSN(List<TreeCleanerAndValidator.XLinneanNameUsage> parents) {
    return parents.stream().map(SimpleName::new).collect(Collectors.toList());
  }

  private Integer sectorSourceDatasetKey(@Nullable Integer sectorKey) {
    return sectorKey == null ? null : sourceBySector.get(sectorKey);
  }
  @Override
  public void end(ParentStack.SNC<TreeCleanerAndValidator.XLinneanNameUsage> snc) {
    // update & store metrics
    final var m = parentsM.pop();
    final var sn = snc.usage;
    m.setId(sn.getId());
    m.setDepth(stack.size());
    m.setMaxDepthIfHigher(stack.size());
    m.setClassification(toSN(stack.getParents(false)));
    metricsMapper.create(m);
    // now add all metrics from this child taxon to it's parent - that's how we aggregate
    if (!parentsM.isEmpty()) {
      // first add this taxon to the metrics to be added to the parent - otherwise we never add anything
      m.incTaxonCount();
      if (sn.getRank() == Rank.SPECIES) {
        Integer sourceKey = sectorSourceDatasetKey(sn.getSectorKey());
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
      if (sn.extinct == null || !sn.extinct) {
        p.incChildExtantCount();
      }
    }
  }
}

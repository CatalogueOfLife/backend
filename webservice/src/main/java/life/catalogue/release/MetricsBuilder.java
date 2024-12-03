package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TaxonMetrics;
import life.catalogue.common.collection.CountMap;

import life.catalogue.db.mapper.TaxonMetricsMapper;

import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

public class MetricsBuilder implements ParentStack.StackHandler<TreeCleanerAndValidator.XLinneanNameUsage> {
  private final ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> stack;
  private final DSID<String> key;
  private final Stack<TaxonMetrics> parentsM = new Stack<>();
  private final TaxonMetricsMapper metricsMapper;

  public MetricsBuilder(ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> stack, int datasetKey, SqlSession session) {
    this.stack = stack;
    this.key = DSID.root(datasetKey);
    this.metricsMapper = session.getMapper(TaxonMetricsMapper.class);
  }

  @Override
  public void start(TreeCleanerAndValidator.XLinneanNameUsage n) {
    parentsM.add(TaxonMetrics.create(key.id(n.getId())));
  }

  private static List<SimpleName> toSN(List<TreeCleanerAndValidator.XLinneanNameUsage> parents) {
    return parents.stream().map(SimpleName::new).collect(Collectors.toList());
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
        m.incSpeciesCount();
        // TODO: deal with counts by sources
        //m.getSpeciesBySourceCount();
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

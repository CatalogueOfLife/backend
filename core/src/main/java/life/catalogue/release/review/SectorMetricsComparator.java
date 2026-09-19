package life.catalogue.release.review;

import life.catalogue.api.model.SectorMetrics;
import life.catalogue.api.model.SectorMetricsDiff;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.SectorImportMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Compares the sector sync metrics of two datasets, normally a release and the previous release of the same kind.
 *
 * This is a plain class on purpose: the REST endpoint and the AI review job both need exactly this, and the job
 * must not reach its own server over HTTP to get it. The endpoint additionally requires editor rights, which the
 * read-only review bot deliberately does not have.
 *
 * The join is on the sector id alone, which is safe because SectorMapper.copyDataset carries the project's sector
 * ids into every release unchanged.
 */
public class SectorMetricsComparator {

  /**
   * The relative change below which a sector that still has usages is not worth reporting.
   */
  public static final double DEFAULT_MIN_CHANGE = 0.1;

  private SectorMetricsComparator() {
  }

  /**
   * Reads the sectors of both datasets and compares them.
   * Resolves each key's project itself, so either key can be a project or a release.
   *
   * @param datasetKey the dataset under review, a project or release
   * @param otherKey   the dataset to compare against, usually the previous release
   * @param minChange  the relative change above which a sector that still has usages is flagged, e.g. 0.1 for 10%
   */
  public static List<SectorMetricsDiff> compare(SqlSessionFactory factory, int datasetKey, int otherKey, double minChange) {
    try (SqlSession session = factory.openSession()) {
      return compare(session, datasetKey, otherKey, minChange);
    }
  }

  /**
   * Same, on an existing session - the request scoped one a resource is handed.
   */
  public static List<SectorMetricsDiff> compare(SqlSession session, int datasetKey, int otherKey, double minChange) {
    // info() rather than keyOrProjectKey(), because it refuses a deleted dataset - comparing against one
    // would silently answer that every sector was removed
    final int projectKey = DatasetInfoCache.CACHE.info(datasetKey).keyOrProjectKey();
    final int otherProjectKey = DatasetInfoCache.CACHE.info(otherKey).keyOrProjectKey();
    var mapper = session.getMapper(SectorImportMapper.class);
    var current = mapper.listMetrics(datasetKey, projectKey);
    var previous = mapper.listMetrics(otherKey, otherProjectKey);
    return compare(current, previous, minChange);
  }

  /**
   * The pure half: joins the two lists by sector id and keeps only the rows a review should look at.
   * Results are sorted by the severity of the flag first and the size of the loss second, so the worst
   * regressions come first however long the list is.
   */
  public static List<SectorMetricsDiff> compare(Collection<SectorMetrics> current, Collection<SectorMetrics> previous, double minChange) {
    Map<Integer, SectorMetrics> prevBySector = new LinkedHashMap<>();
    if (previous != null) {
      for (var p : previous) {
        prevBySector.put(p.getSectorKey(), p);
      }
    }
    List<SectorMetricsDiff> diffs = new ArrayList<>();
    if (current != null) {
      for (var c : current) {
        var d = diff(c, prevBySector.remove(c.getSectorKey()), minChange);
        if (d != null) {
          diffs.add(d);
        }
      }
    }
    // whatever is left of the previous dataset no longer exists here
    for (var p : prevBySector.values()) {
      var d = diff(null, p, minChange);
      if (d != null) {
        diffs.add(d);
      }
    }
    // flag declaration order first (ZERO leads), then the largest loss of usages, then the sector id
    diffs.sort(Comparator
      .comparingInt((SectorMetricsDiff d) -> d.getFlag().ordinal())
      .thenComparingInt(d -> d.getUsagesCount() - d.getPrevUsagesCount())
      .thenComparingInt(SectorMetricsDiff::getSectorKey));
    return diffs;
  }

  /**
   * Classifies a single sector.
   *
   * @return the diff if the sector is worth reporting, null if it changed too little to be of interest
   */
  static @Nullable SectorMetricsDiff diff(@Nullable SectorMetrics cur, @Nullable SectorMetrics prev, double minChange) {
    if (cur == null && prev == null) {
      return null;
    }
    final int usages = cur == null ? 0 : cur.getUsagesCount();
    final int prevUsages = prev == null ? 0 : prev.getUsagesCount();
    final Double change = relativeChange(usages, prevUsages);

    final SectorMetricsDiff.Flag flag;
    if (cur == null) {
      flag = SectorMetricsDiff.Flag.REMOVED;
    } else if (prev == null) {
      flag = SectorMetricsDiff.Flag.NEW;
    } else if (usages == 0 && prevUsages > 0) {
      // the sector lost everything - the single worst thing that can happen to a release
      flag = SectorMetricsDiff.Flag.ZERO;
    } else if (change == null) {
      // nothing to grow from: report it as growth, there is no meaningful percentage
      flag = usages > 0 ? SectorMetricsDiff.Flag.INCREASED : null;
    } else if (Math.abs(change) > minChange) {
      flag = change > 0 ? SectorMetricsDiff.Flag.INCREASED : SectorMetricsDiff.Flag.DECREASED;
    } else {
      flag = null;
    }
    if (flag == null) {
      return null;
    }

    var d = new SectorMetricsDiff();
    // the descriptive columns come from the current sector where there is one, else from the removed one
    var src = cur != null ? cur : prev;
    d.setSectorKey(src.getSectorKey());
    d.setMode(src.getMode());
    d.setSubjectDatasetKey(src.getSubjectDatasetKey());
    d.setSubjectName(src.getSubjectName());
    d.setTargetName(src.getTargetName());
    d.setTargetRank(src.getTargetRank());
    d.setAttempt(cur == null ? null : cur.getAttempt());
    d.setPrevAttempt(prev == null ? null : prev.getAttempt());
    d.setUsagesCount(usages);
    d.setPrevUsagesCount(prevUsages);
    d.setTaxonCount(cur == null ? null : cur.getTaxonCount());
    d.setSynonymCount(cur == null ? null : cur.getSynonymCount());
    d.setChange(change);
    d.setFlag(flag);
    return d;
  }

  /**
   * @return (current-previous)/previous, or null when previous is zero and there is nothing to divide by
   */
  static @Nullable Double relativeChange(int usages, int prevUsages) {
    if (prevUsages == 0) {
      return usages == 0 ? Double.valueOf(0d) : null;
    }
    return (usages - (double) prevUsages) / prevUsages;
  }
}

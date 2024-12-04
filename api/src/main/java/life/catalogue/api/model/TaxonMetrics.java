package life.catalogue.api.model;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.common.collection.CountMap;

import org.gbif.nameparser.api.Rank;

import java.util.*;

/**
 * Metrics about descendants for a single taxon, i.e. accepted name usage.
 * Taxon metrics only exist for quasi immutable datasets, i.e. releases or external dataset but not projects!
 *
 * The id value of the metric is the taxonID of its related taxon!
 */
public class TaxonMetrics extends DatasetScopedEntityNotManaged<String> {
  private int depth; // number of parent taxa existing above
  private int maxDepth; // maximum depth of the tree below, starting from root not this taxon, found in any of the descendants incl synonyms
  private int taxonCount; // total count of all accepted names in the subtree, no matter their rank
  private int childCount;
  private int childExtantCount;
  private Map<Rank, Integer> taxaByRankCount;
  private Map<Integer, Integer> speciesBySourceCount;

  private IntSet sourceDatasetKeys;
  /**
   * Only the db simple_name type properties are stored:
   *  id, rank, name, authorship
   */
  private List<SimpleName> classification;

  public static TaxonMetrics create(DSID<String> key) {
    TaxonMetrics tm = new TaxonMetrics();
    tm.setKey(key);
    tm.setTaxaByRankCount(new CountMap<>());
    tm.setSpeciesBySourceCount(new CountMap<>());
    tm.sourceDatasetKeys = new IntOpenHashSet();
    return tm;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public void setMaxDepthIfHigher(int depth) {
    if (depth > maxDepth) {
      this.maxDepth = depth;
    }
  }

  public int getTaxonCount() {
    return taxonCount;
  }

  public void setTaxonCount(int taxonCount) {
    this.taxonCount = taxonCount;
  }
  public void incTaxonCount() {
    this.taxonCount++;
  }

  public int getSpeciesCount() {
    return taxaByRankCount == null || !taxaByRankCount.containsKey(Rank.SPECIES) ? 0 : taxaByRankCount.get(Rank.SPECIES);
  }

  public int getChildCount() {
    return childCount;
  }

  public void setChildCount(int childCount) {
    this.childCount = childCount;
  }

  public void incChildCount() {
    this.childCount++;
  }
  public int getChildExtantCount() {
    return childExtantCount;
  }

  public void setChildExtantCount(int childExtantCount) {
    this.childExtantCount = childExtantCount;
  }
  public void incChildExtantCount() {
    this.childExtantCount++;
  }

  public Map<Rank, Integer> getTaxaByRankCount() {
    return taxaByRankCount;
  }

  public void setTaxaByRankCount(Map<Rank, Integer> taxaByRankCount) {
    this.taxaByRankCount = taxaByRankCount;
  }

  public Map<Integer, Integer> getSpeciesBySourceCount() {
    return speciesBySourceCount;
  }

  public void setSpeciesBySourceCount(Map<Integer, Integer> speciesBySourceCount) {
    this.speciesBySourceCount = speciesBySourceCount;
  }

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public IntSet getSourceDatasetKeys() {
    return sourceDatasetKeys;
  }

  public void setSourceDatasetKeys(IntSet sourceDatasetKeys) {
    this.sourceDatasetKeys = sourceDatasetKeys;
  }

  /**
   * Warning - requires CountMap instances as metrics maps!!!
   */
  public void add(TaxonMetrics m) {
    setMaxDepthIfHigher(m.maxDepth);
    taxonCount += m.taxonCount;
    ((CountMap<Rank>)taxaByRankCount).inc(((CountMap<Rank>)m.taxaByRankCount));
    ((CountMap<Integer>)speciesBySourceCount).inc(((CountMap<Integer>)m.speciesBySourceCount));
    sourceDatasetKeys.addAll(m.sourceDatasetKeys);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaxonMetrics)) return false;
    TaxonMetrics that = (TaxonMetrics) o;
    return depth == that.depth && maxDepth == that.maxDepth && taxonCount == that.taxonCount && childCount == that.childCount && childExtantCount == that.childExtantCount && Objects.equals(taxaByRankCount, that.taxaByRankCount) && Objects.equals(speciesBySourceCount, that.speciesBySourceCount) && Objects.equals(sourceDatasetKeys, that.sourceDatasetKeys) && Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(depth, maxDepth, taxonCount, childCount, childExtantCount, taxaByRankCount, speciesBySourceCount, sourceDatasetKeys, classification);
  }
}

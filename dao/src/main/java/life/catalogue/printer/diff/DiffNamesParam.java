package life.catalogue.printer.diff;

import org.gbif.nameparser.api.Rank;

import java.util.Set;

/** Parameters for the pure-SQL dataset name-label generator (NameUsageMapper.processDiffNames). */
public class DiffNamesParam {
  private int datasetKey;
  private Set<String> roots;         // null/empty = whole dataset (start at parent_id IS NULL)
  private Set<String> exclusion;     // taxonIDs to prune (incl. descendants)
  private Rank lowestRank;
  private boolean synonyms = true;
  private Set<Rank> rankFilter;      // ranks to exclude from output (row-level, keeps descendants)
  private boolean authorship = true;
  private boolean parentName = false;
  private Rank parentRank;           // null = direct parent; else ancestor at this rank
  private boolean ignoreAmbiguousRanks = false;

  public int getDatasetKey() { return datasetKey; }
  public void setDatasetKey(int datasetKey) { this.datasetKey = datasetKey; }
  public Set<String> getRoots() { return roots; }
  public void setRoots(Set<String> roots) { this.roots = roots; }
  public Set<String> getExclusion() { return exclusion; }
  public void setExclusion(Set<String> exclusion) { this.exclusion = exclusion; }
  public Rank getLowestRank() { return lowestRank; }
  public void setLowestRank(Rank lowestRank) { this.lowestRank = lowestRank; }
  public boolean isSynonyms() { return synonyms; }
  public void setSynonyms(boolean synonyms) { this.synonyms = synonyms; }
  public Set<Rank> getRankFilter() { return rankFilter; }
  public void setRankFilter(Set<Rank> rankFilter) { this.rankFilter = rankFilter; }
  public boolean isAuthorship() { return authorship; }
  public void setAuthorship(boolean authorship) { this.authorship = authorship; }
  public boolean isParentName() { return parentName; }
  public void setParentName(boolean parentName) { this.parentName = parentName; }
  public Rank getParentRank() { return parentRank; }
  public void setParentRank(Rank parentRank) { this.parentRank = parentRank; }
  public boolean isIgnoreAmbiguousRanks() { return ignoreAmbiguousRanks; }
  public void setIgnoreAmbiguousRanks(boolean v) { this.ignoreAmbiguousRanks = v; }
}

package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.Set;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

public class TreeTraversalParameter {
  /**
   * The dataset to traverse
   */
  private int datasetKey;

  /**
   * taxon id to start the traversal. Will be included in the result. If null start with all root taxa
   */
  @QueryParam("taxonID")
  private String taxonID;

  /**
   * Set of taxon ids to exclude from traversal. This will also exclude all descendants
   */
  @QueryParam("exclusion")
  private Set<String> exclusion; // taxonIDs

  /**
   * optional rank cutoff filter to only include children with a rank above or equal to the one given
   */
  @QueryParam("lowestRank")
  private Rank lowestRank;

  /**
   * if true includes synonyms (default), otherwise only taxa
   */
  @QueryParam("sectorKey")
  @DefaultValue("true")
  private boolean synonyms = true;

  public TreeTraversalParameter() {
  }

  public TreeTraversalParameter(TreeTraversalParameter other) {
    this.datasetKey = other.datasetKey;
    this.taxonID = other.taxonID;
    this.exclusion = other.exclusion;
    this.lowestRank = other.lowestRank;
    this.synonyms = other.synonyms;
  }

  public static TreeTraversalParameter all(int datasetKey, String taxonID, Set<String> exclusion, Rank lowestRank, boolean synonyms) {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setTaxonID(taxonID);
    ttp.setExclusion(exclusion);
    ttp.setLowestRank(lowestRank);
    ttp.setSynonyms(synonyms);
    return ttp;
  }

  public static TreeTraversalParameter dataset(int datasetKey) {
    return dataset(datasetKey, null, null);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID) {
    return dataset(datasetKey, taxonID, null);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID, Set<String> exclusions) {
    return dataset(datasetKey, taxonID, exclusions, null, true);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID, Set<String> exclusions, Rank lowestRank, boolean synonyms) {
    var ttp = new TreeTraversalParameter();
    ttp.setDatasetKey(datasetKey);
    ttp.setTaxonID(taxonID);
    ttp.setExclusion(exclusions);
    ttp.setLowestRank(lowestRank);
    ttp.setSynonyms(synonyms);
    return ttp;
  }

  public static TreeTraversalParameter datasetNoSynonyms(int datasetKey) {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setSynonyms(false);
    return ttp;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public String getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }

  public Set<String> getExclusion() {
    return exclusion;
  }

  public void setExclusion(Set<String> exclusion) {
    this.exclusion = exclusion;
  }

  public Rank getLowestRank() {
    return lowestRank;
  }

  public void setLowestRank(Rank lowestRank) {
    this.lowestRank = lowestRank;
  }

  public boolean isSynonyms() {
    return synonyms;
  }

  public void setSynonyms(boolean synonyms) {
    this.synonyms = synonyms;
  }

  /**
   * @return true if any filter has been used apart from the mandatory datasetKey
   */
  public boolean hasFilter() {
    return !synonyms || taxonID != null || lowestRank != null || (exclusion != null && !exclusion.isEmpty());
  }

  @Override
  public String toString() {
    return "TreeTraversalParameter{" +
           "datasetKey=" + datasetKey +
           ", rootTaxonID='" + taxonID + '\'' +
           ", exclusions=" + exclusion +
           ", lowestRank=" + lowestRank +
           ", synonyms=" + synonyms +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TreeTraversalParameter)) return false;
    TreeTraversalParameter that = (TreeTraversalParameter) o;
    return datasetKey == that.datasetKey
           && synonyms == that.synonyms
           && Objects.equals(taxonID, that.taxonID)
           && Objects.equals(exclusion, that.exclusion)
           && lowestRank == that.lowestRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, taxonID, exclusion, lowestRank, synonyms);
  }
}

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
   * An optional sector to limit traversal to
   */
  private Integer sectorKey;

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
   * optional filter for explicitly only extinct or only extant records. Null includes all.
   */
  @QueryParam("extinct")
  private Boolean extinct;

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
    this.sectorKey = other.sectorKey;
    this.taxonID = other.taxonID;
    this.exclusion = other.exclusion;
    this.lowestRank = other.lowestRank;
    this.extinct = other.extinct;
    this.synonyms = other.synonyms;
  }

  public static TreeTraversalParameter all(int datasetKey, Integer sectorKey, String taxonID, Set<String> exclusion, Rank lowestRank, Boolean extinct, boolean synonyms) {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setSectorKey(sectorKey);
    ttp.setTaxonID(taxonID);
    ttp.setExclusion(exclusion);
    ttp.setLowestRank(lowestRank);
    ttp.setExtinct(extinct);
    ttp.setSynonyms(synonyms);
    return ttp;
  }

  public static TreeTraversalParameter sectorTarget(Sector sector) {
    var ttp = dataset(sector.getDatasetKey());
    ttp.setSectorKey(sector.getId());
    ttp.setTaxonID(sector.getTargetID());
    return ttp;
  }

  public static TreeTraversalParameter sectorSubject(Sector sector) {
    var ttp = dataset(sector.getSubjectDatasetKey());
    ttp.setTaxonID(sector.getSubjectID());
    return ttp;
  }

  public static TreeTraversalParameter dataset(int datasetKey) {
    return dataset(datasetKey, null, null);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID) {
    return dataset(datasetKey, taxonID, null);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID, Set<String> exclusions) {
    return dataset(datasetKey, taxonID, exclusions, null, null, true);
  }

  public static TreeTraversalParameter dataset(int datasetKey, String taxonID, Set<String> exclusions, Rank lowestRank, Boolean extinct, boolean synonyms) {
    var ttp = new TreeTraversalParameter();
    ttp.setDatasetKey(datasetKey);
    ttp.setTaxonID(taxonID);
    ttp.setExclusion(exclusions);
    ttp.setLowestRank(lowestRank);
    ttp.setExtinct(extinct);
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

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
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

  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
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
    return !synonyms || extinct!=null || sectorKey!=null || taxonID != null || lowestRank != null || (exclusion != null && !exclusion.isEmpty());
  }

  @Override
  public String toString() {
    return "TreeTraversalParameter{" +
           "datasetKey=" + datasetKey +
           ", sectorKey=" + sectorKey +
           ", rootTaxonID='" + taxonID + '\'' +
           ", exclusions=" + exclusion +
           ", lowestRank=" + lowestRank +
           ", extinct=" + extinct +
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
           && Objects.equals(sectorKey, that.sectorKey)
           && Objects.equals(taxonID, that.taxonID)
           && Objects.equals(exclusion, that.exclusion)
           && lowestRank == that.lowestRank
           && Objects.equals(extinct, that.extinct);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, sectorKey, taxonID, exclusion, lowestRank, extinct, synonyms);
  }
}

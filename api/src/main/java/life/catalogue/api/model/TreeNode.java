package life.catalogue.api.model;

import life.catalogue.api.vocab.EstimateType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * A drastic simplification of a taxon with just the minimum information used to render in a tree.
 * Adds various additional infos to support the assembly tree.
 */
public abstract class TreeNode implements DSID<String> {

  public static enum Type {
    CATALOGUE,
    SOURCE;
  }

  /**
   * @return all tree types incl null
   */
  public static List<TreeNode.Type> types(){
    List<TreeNode.Type> types = new ArrayList<>();
    types.add(null);
    types.addAll(Arrays.asList(TreeNode.Type.values()));
    return types;
  }

  private Integer datasetKey;
  private String id;
  private String parentId;
  private Rank rank;
  private TaxonomicStatus status;
  private Integer count;
  private int childCount;
  private List<SpeciesEstimate> estimates;
  private Integer sectorKey;
  private Integer sectorDatasetKey;
  private Boolean sectorRoot;
  private EditorialDecision decision;
  // maps of included sectors, aggregated by their subject dataset key. This EXCLUDES the sectorKey of this node
  private Int2IntOpenHashMap datasetSectors;
  
  /**
   * Exposes a structured name instance as a full name with html markup
   * instead of the regular name property.
   * Only to be used by mybatis mappers, nowhere else!!!
   */
  public static class TreeNodeMybatis extends TreeNode {
    private Name _name;
    private String namePhrase;
    private Boolean extinct;

    @Override
    public String getName() {
      return _name.getScientificName();
    }

    @Override
    public String getAuthorship() {
      return _name.getAuthorship();
    }

    @Override
    public String getLabelHtml() {
      return NameUsageBase.getLabelBuilder(_name, extinct, namePhrase, null, true).toString();
    }
  }

  public static class PlaceholderNode extends TreeNode {
    private static String NAME = "Not assigned";

    @Override
    public String getAuthorship() {
      return null;
    }

    @Override
    public String getLabelHtml() {
      return NAME;
    }

    @Override
    public String getName() {
      return NAME;
    }
  }

  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  @Override
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }
  
  public String getParentId() {
    return parentId;
  }
  
  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public abstract String getAuthorship();

  public abstract String getLabelHtml();

  public abstract String getName();

  public Rank getRank() {
    return rank;
  }
  
  public void setRank(Rank rank) {
    this.rank = rank;
  }
  
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public int getChildCount() {
    return childCount;
  }

  public void setChildCount(int childCount) {
    this.childCount = childCount;
  }

  public Integer getCount() {
    return count;
  }
  
  public void setCount(Integer count) {
    this.count = count;
  }
  
  public List<SpeciesEstimate> getEstimates() {
    return estimates;
  }
  
  public void setEstimates(List<SpeciesEstimate> estimates) {
    this.estimates = estimates;
  }
  
  /**
   * @return the average of the listed DESCRIBED_SPECIES_LIVING estimates
   */
  public Integer getEstimate() {
    if (estimates == null || estimates.isEmpty()) {
      return null;
    }
    double avg = estimates.stream()
        .filter(e -> e.getEstimate() != null)
        .filter(e -> e.getType() == EstimateType.SPECIES_LIVING)
        .collect(Collectors.averagingInt(SpeciesEstimate::getEstimate));
    return avg == 0 ? null : (int) avg;
  }

  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public Integer getSectorDatasetKey() {
    return sectorDatasetKey;
  }

  public void setSectorDatasetKey(Integer sectorDatasetKey) {
    this.sectorDatasetKey = sectorDatasetKey;
  }

  public Boolean getSectorRoot() {
    return sectorRoot;
  }

  public void setSectorRoot(Boolean sectorRoot) {
    this.sectorRoot = sectorRoot;
  }

  public EditorialDecision getDecision() {
    return decision;
  }
  
  public void setDecision(EditorialDecision decision) {
    this.decision = decision;
  }
  
  public Int2IntOpenHashMap getDatasetSectors() {
    return datasetSectors;
  }
  
  public void setDatasetSectors(Int2IntOpenHashMap datasetSectors) {
    this.datasetSectors = datasetSectors;
  }

  @JsonIgnore
  public boolean isPlaceholder() {
    return this instanceof PlaceholderNode;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TreeNode)) return false;
    TreeNode treeNode = (TreeNode) o;
    return childCount == treeNode.childCount &&
           Objects.equals(count, treeNode.count) &&
           Objects.equals(datasetKey, treeNode.datasetKey) &&
           Objects.equals(id, treeNode.id) &&
           Objects.equals(parentId, treeNode.parentId) &&
          rank == treeNode.rank &&
          status == treeNode.status &&
           Objects.equals(estimates, treeNode.estimates) &&
           Objects.equals(sectorKey, treeNode.sectorKey) &&
           Objects.equals(sectorDatasetKey, treeNode.sectorDatasetKey) &&
           Objects.equals(sectorRoot, treeNode.sectorRoot) &&
           Objects.equals(decision, treeNode.decision) &&
           Objects.equals(datasetSectors, treeNode.datasetSectors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, parentId, rank, status, count, childCount, estimates, sectorKey, sectorDatasetKey, sectorRoot, decision, datasetSectors);
  }

  @Override
  public String toString() {
    return rank + " " + getName() + " " + getAuthorship() + " [" + id +"]";
  }
}

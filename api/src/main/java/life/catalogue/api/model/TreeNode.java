package life.catalogue.api.model;

import life.catalogue.api.vocab.EstimateType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * A drastic simplification of a taxon with just the minimum information used to render in a tree.
 * Adds various additional infos to support the assembly tree.
 */
public abstract class TreeNode implements DSID<String>, SectorScoped {

  public static enum Type {
    PROJECT,
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
  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer sectorDatasetKey;
  private Boolean sectorRoot;
  private EditorialDecision decision;
  private IntSet sourceDatasetKeys;
  private Map<UUID, IntSet> publisherDatasetKeys;

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
      return NameUsageBase.labelBuilder(_name, extinct, getStatus(), namePhrase, null, true).toString();
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

  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
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

  public IntSet getSourceDatasetKeys() {
    return sourceDatasetKeys;
  }

  public void setSourceDatasetKeys(IntSet sourceDatasetKeys) {
    this.sourceDatasetKeys = sourceDatasetKeys;
  }

  public Map<UUID, IntSet> getPublisherDatasetKeys() {
    return publisherDatasetKeys;
  }

  public void setPublisherDatasetKeys(Map<UUID, IntSet> publisherDatasetKeys) {
    this.publisherDatasetKeys = publisherDatasetKeys;
  }

  @JsonIgnore
  public boolean isPlaceholder() {
    return this instanceof PlaceholderNode;
  }


  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TreeNode)) return false;
    TreeNode treeNode = (TreeNode) o;
    return childCount == treeNode.childCount && Objects.equals(datasetKey, treeNode.datasetKey) && Objects.equals(id, treeNode.id) && Objects.equals(parentId, treeNode.parentId) && rank == treeNode.rank && status == treeNode.status && Objects.equals(count, treeNode.count) && Objects.equals(sectorKey, treeNode.sectorKey) && sectorMode == treeNode.sectorMode && Objects.equals(sectorDatasetKey, treeNode.sectorDatasetKey) && Objects.equals(sectorRoot, treeNode.sectorRoot) && Objects.equals(decision, treeNode.decision) && Objects.equals(sourceDatasetKeys, treeNode.sourceDatasetKeys) && Objects.equals(publisherDatasetKeys, treeNode.publisherDatasetKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, parentId, rank, status, count, childCount, sectorKey, sectorMode, sectorDatasetKey, sectorRoot, decision, sourceDatasetKeys, publisherDatasetKeys);
  }

  @Override
  public String toString() {
    return rank + " " + getName() + " " + getAuthorship() + " [" + id +"]";
  }
}

package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

/**
 * A drastic simplification of a taxon with just the minimum information used to render in a tree.
 */
public class TreeNode implements ID {

  private Integer datasetKey;
  private String id;
  private String parentId;
  private String name;
  private Rank rank;
  private TaxonomicStatus status;
  private int childCount;
  private Integer speciesEstimate;
  private String speciesEstimateReferenceId;
  private Integer sectorKey;
  private Sector sector;
  private Decision decision;
  
  /**
   * Only to be used by mybatis mappers, nowhere else!!!
   */
  public static class TreeNodeMybatis extends TreeNode {
    private Name _name;
  
    @Override
    public String getName() {
      return _name == null ? null :_name.canonicalNameCompleteHtml();
    }
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }

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
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
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
  
  public Integer getSpeciesEstimate() {
    return speciesEstimate;
  }
  
  public void setSpeciesEstimate(Integer speciesEstimate) {
    this.speciesEstimate = speciesEstimate;
  }
  
  public String getSpeciesEstimateReferenceId() {
    return speciesEstimateReferenceId;
  }
  
  public void setSpeciesEstimateReferenceId(String speciesEstimateReferenceId) {
    this.speciesEstimateReferenceId = speciesEstimateReferenceId;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TreeNode treeNode = (TreeNode) o;
    return childCount == treeNode.childCount &&
        Objects.equals(datasetKey, treeNode.datasetKey) &&
        Objects.equals(id, treeNode.id) &&
        Objects.equals(parentId, treeNode.parentId) &&
        Objects.equals(name, treeNode.name) &&
        Objects.equals(rank, treeNode.rank) &&
        Objects.equals(status, treeNode.status) &&
        Objects.equals(speciesEstimate, treeNode.speciesEstimate) &&
        Objects.equals(speciesEstimateReferenceId, treeNode.speciesEstimateReferenceId) &&
        Objects.equals(sectorKey, treeNode.sectorKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, parentId, name, rank, status, childCount, speciesEstimate, speciesEstimateReferenceId, sectorKey);
  }
}

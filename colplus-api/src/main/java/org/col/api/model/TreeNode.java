package org.col.api.model;

import java.util.Objects;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

/**
 * A drastic simplification of a taxon with just the minimum information used to render in a tree.
 * Adds various additional infos to support the assembly tree.
 */
public class TreeNode implements DatasetEntity {

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
  private Decision decision;
  private Int2IntMap datasetSectors;
  
  /**
   * Exposes a structured name instance as a full name with html markup
   * instead of the regular name property.
   * Only to be used by mybatis mappers, nowhere else!!!
   */
  public static class TreeNodeMybatis extends TreeNode {
    private Name _name;
  
    @Override
    public String getName() {
      return _name == null ? super.name :_name.canonicalNameCompleteHtml();
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
  
  public Decision getDecision() {
    return decision;
  }
  
  public void setDecision(Decision decision) {
    this.decision = decision;
  }
  
  public Int2IntMap getDatasetSectors() {
    return datasetSectors;
  }
  
  public void setDatasetSectors(Int2IntMap datasetSectors) {
    this.datasetSectors = datasetSectors;
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
        rank == treeNode.rank &&
        status == treeNode.status &&
        Objects.equals(speciesEstimate, treeNode.speciesEstimate) &&
        Objects.equals(speciesEstimateReferenceId, treeNode.speciesEstimateReferenceId) &&
        Objects.equals(sectorKey, treeNode.sectorKey) &&
        Objects.equals(decision, treeNode.decision) &&
        Objects.equals(datasetSectors, treeNode.datasetSectors);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, parentId, name, rank, status, childCount, speciesEstimate, speciesEstimateReferenceId, sectorKey, decision, datasetSectors);
  }
}

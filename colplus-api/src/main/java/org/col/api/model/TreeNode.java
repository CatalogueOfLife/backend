package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

/**
 * A drastic simplification of a taxon with just the minimum information used to render in a tree.
 */
public class TreeNode {
  
  private String id;
  private String parentId;
  private String name;
  private String authorship;
  private Rank rank;
  private TaxonomicStatus status;
  private int childCount;
  private int speciesCount;
  private Integer speciesEstimate;
  private String speciesEstimateReferenceId;
  private Sector sector;
  
  public String getId() {
    return id;
  }
  
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
  
  public String getAuthorship() {
    return authorship;
  }
  
  public void setAuthorship(String authorship) {
    this.authorship = authorship;
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
  
  public int getSpeciesCount() {
    return speciesCount;
  }
  
  public void setSpeciesCount(int speciesCount) {
    this.speciesCount = speciesCount;
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
  
  public Sector getSector() {
    return sector;
  }
  
  public void setSector(Sector sector) {
    this.sector = sector;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TreeNode treeNode = (TreeNode) o;
    return childCount == treeNode.childCount &&
        speciesCount == treeNode.speciesCount &&
        Objects.equals(id, treeNode.id) &&
        Objects.equals(parentId, treeNode.parentId) &&
        Objects.equals(name, treeNode.name) &&
        Objects.equals(authorship, treeNode.authorship) &&
        rank == treeNode.rank &&
        status == treeNode.status &&
        Objects.equals(speciesEstimate, treeNode.speciesEstimate) &&
        Objects.equals(speciesEstimateReferenceId, treeNode.speciesEstimateReferenceId) &&
        Objects.equals(sector, treeNode.sector);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(id, parentId, name, authorship, rank, status, childCount, speciesCount, speciesEstimate, speciesEstimateReferenceId, sector);
  }
}

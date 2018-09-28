package org.col.api.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A taxonomic sector definition within a dataset that is used to assemble the Catalogue of Life.
 * Sectors will also serve to show the taxonomic coverage in the CoL portal.
 */
public class Sector {
  private Integer key;
  private Integer colSourceKey;
  private NameRef root;
  private List<NameRef> exclude;
  private NameRef attachment;
  private List<NameRef> suppress;
  private LocalDateTime created;
  private LocalDateTime modified;
  
  /**
   * Primary key
   */
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  /**
   * The col source the root of this sector originates from
   */
  public Integer getColSourceKey() {
    return colSourceKey;
  }
  
  public void setColSourceKey(Integer colSourceKey) {
    this.colSourceKey = colSourceKey;
  }
  
  /**
   * A reference to the single root taxon from the col source for this sector.
   * Can even be a species, but usually some higher taxon.
   */
  public NameRef getRoot() {
    return root;
  }
  
  public void setRoot(NameRef root) {
    this.root = root;
  }
  
  /**
   * Optional list of taxa within the descendants of root to exclude from this sector definition
   */
  public List<NameRef> getExclude() {
    return exclude;
  }
  
  public void setExclude(List<NameRef> exclude) {
    this.exclude = exclude;
  }
  
  /**
   * The attachment point in the CoL tree, i.e. the CoL parent taxon for the sector root
   */
  public NameRef getAttachment() {
    return attachment;
  }
  
  public void setAttachment(NameRef attachment) {
    this.attachment = attachment;
  }
  
  /**
   * Optional list of CoL tree taxa that should be suppressed when this sector is attached.
   * E.g. the CoL management classification might define an alternative taxonomy that uses
   * different taxa which are covered by this sector already.
   */
  public List<NameRef> getSuppress() {
    return suppress;
  }
  
  public void setSuppress(List<NameRef> suppress) {
    this.suppress = suppress;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  /**
   * Time the data of this sector was last changed in the Catalogue of Life.
   */
  public LocalDateTime getModified() {
    return modified;
  }
  
  public void setModified(LocalDateTime modified) {
    this.modified = modified;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Sector sector = (Sector) o;
    return Objects.equals(key, sector.key) &&
        Objects.equals(colSourceKey, sector.colSourceKey) &&
        Objects.equals(root, sector.root) &&
        Objects.equals(exclude, sector.exclude) &&
        Objects.equals(attachment, sector.attachment) &&
        Objects.equals(suppress, sector.suppress) &&
        Objects.equals(created, sector.created) &&
        Objects.equals(modified, sector.modified);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(key, colSourceKey, root, exclude, attachment, suppress, created, modified);
  }
  
  @Override
  public String toString() {
    return "Sector{" + key +
        ", colSourceKey=" + colSourceKey +
        ", root=" + root +
        '}';
  }
}

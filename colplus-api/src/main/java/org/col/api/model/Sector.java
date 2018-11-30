package org.col.api.model;

import java.util.List;

/**
 * A taxonomic sector definition within a dataset that is used to assemble the Catalogue of Life.
 * Sectors will also serve to show the taxonomic coverage in the CoL portal.
 * The subject of the sector is the root taxon in the original source dataset.
 * The target is the parent taxon the subject should be placed under in the catalogue in ATTACH mode which is the default.
 * In MERGE mode the subject taxon itself should be skipped and only its descendants be included.
 *
 * A sector can be really small and the subject even be a species, but usually it is some higher taxon.
 */
public class Sector extends Decision {
  private Integer colSourceKey;
  private NameRef target;
  private List<NameRef> exclude;
  private Mode mode = Sector.Mode.ATTACH;
  
  public static enum Mode {
    /**
     * Attach the entire subject and its descendants under its target parent.
     */
    ATTACH,

    /**
     * Merge all descendants of subject under the target taxon, but exclude the subject taxon itself.
     */
    MERGE
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
   * Optional list of taxa within the descendants of root to exclude from this sector definition
   */
  public List<NameRef> getExclude() {
    return exclude;
  }
  
  public void setExclude(List<NameRef> exclude) {
    this.exclude = exclude;
  }
  
  public Mode getMode() {
    return mode;
  }
  
  public void setMode(Mode mode) {
    this.mode = mode;
  }
  
  /**
   * The attachment point in the CoL tree, i.e. the CoL parent taxon for the sector root
   */
  public NameRef getTarget() {
    return target;
  }
  
  public void setTarget(NameRef target) {
    this.target = target;
  }
  
  
  @Override
  public String toString() {
    return "Sector{" + getKey() +
        ", colSourceKey=" + colSourceKey +
        ", mode=" + mode +
        ", subject=" + getSubject() +
        '}';
  }
}

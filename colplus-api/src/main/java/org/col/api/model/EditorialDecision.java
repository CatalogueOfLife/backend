package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;

/**
 * A single, persisted editorial decision about a single name/taxon
 * within a given CoL sector.
 *
 * A decision can change a record or block it and all its descendants entirely.
 * If blocked all further configured changes are ignored.
 * Otherwise all non null values from status or name should be applied to the underlying subject.
 */
public class EditorialDecision extends Decision {
  private Mode mode;
  private TaxonomicStatus status;
  private Name name;
  
  public static enum Mode {
    /**
     * Blocks the subject and all its descendants.
     */
    BLOCK,
  
    /**
     * Flags a name as a chresonym and blocks it from the assembly.
     */
    CHRESONYM,

    /**
     * Updates the subject using the configured name and status,
     * leaving NULL values unchanged.
     */
    UPDATE,
  
    /**
     * Creates a new taxon child below the subject using the configured name and status.
     */
    CREATE
  }
  
  public Mode getMode() {
    return mode;
  }
  
  public void setMode(Mode mode) {
    this.mode = mode;
  }
  
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }
  
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EditorialDecision that = (EditorialDecision) o;
    return mode == that.mode &&
        status == that.status &&
        Objects.equals(name, that.name);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(super.hashCode(), mode, status, name);
  }
  
  @Override
  public String toString() {
    return "EditorialDecision{" + getKey() + " " + mode + " on " + subject + '}';
  }
}

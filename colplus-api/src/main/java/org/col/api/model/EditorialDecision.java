package org.col.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Lifezone;
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
  private Name name;
  private TaxonomicStatus status;
  private Boolean fossil;
  private Boolean recent;
  private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);
  
  public static enum Mode {
    /**
     * Blocks the subject and all its descendants.
     */
    BLOCK,
  
    /**
     * Flags a name as a chresonym and blocks it from the assembly, keeping potential children.
     */
    CHRESONYM,

    /**
     * Updates the subject using the configured name, status, lifezone and fossil flags
     * leaving NULL values unchanged.
     */
    UPDATE,
  
    /**
     * Updates the subject and all its descendants using the configured status, lifezone and fossil flags
     * leaving NULL values unchanged.
     *
     * If configured, Name updates will be ignored!!!
     */
    UPDATE_RECURSIVE
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
  
  public Boolean getFossil() {
    return fossil;
  }
  
  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }
  
  public Boolean getRecent() {
    return recent;
  }
  
  public void setRecent(Boolean recent) {
    this.recent = recent;
  }
  
  public Set<Lifezone> getLifezones() {
    return lifezones;
  }
  
  public void setLifezones(Set<Lifezone> lifezones) {
    this.lifezones = lifezones;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EditorialDecision that = (EditorialDecision) o;
    return mode == that.mode &&
        Objects.equals(name, that.name) &&
        status == that.status &&
        Objects.equals(fossil, that.fossil) &&
        Objects.equals(recent, that.recent) &&
        Objects.equals(lifezones, that.lifezones);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), mode, name, status, fossil, recent, lifezones);
  }
  
  @Override
  public String toString() {
    return "EditorialDecision{" + getKey() + " " + mode + " on " + subject + '}';
  }
}

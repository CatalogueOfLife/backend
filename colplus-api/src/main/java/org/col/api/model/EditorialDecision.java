package org.col.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class EditorialDecision extends DataEntity<Integer> implements DatasetScoped {
  private Integer key;
  private Integer datasetKey; // the catalogues datasetKey
  private SimpleName subject;
  private Integer subjectDatasetKey; // the datasetKey the subject belongs to, not the catalogue!
  private Mode mode;
  private Name name;
  private TaxonomicStatus status;
  private Boolean extinct;
  private String temporalRangeStart;
  private String temporalRangeEnd;
  private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);
  private String note;
  
  public static enum Mode {
    /**
     * Blocks the subject and all its descendants.
     */
    BLOCK,
  
    /**
     * Flags a name as reviewed and accepted as it is.
     */
    REVIEWED,

    /**
     * Updates the subject using the configured name, status, lifezone and extinct flag
     * leaving NULL values unchanged.
     */
    UPDATE,
  
    /**
     * Updates the subject and all its descendants using the configured status, lifezone and extinct flag
     * leaving NULL values unchanged.
     *
     * If configured, Name updates will be ignored!!!
     */
    UPDATE_RECURSIVE
  }
  
  @Override
  public Integer getKey() {
    return key;
  }
  
  @Override
  public void setKey(Integer key) {
    this.key = key;
  }
  
  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  @Override
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public SimpleName getSubject() {
    return subject;
  }
  
  public void setSubject(SimpleName subject) {
    this.subject = subject;
  }
  
  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
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
  
  public Boolean isExtinct() {
    return extinct;
  }
  
  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }
  
  public String getTemporalRangeStart() {
    return temporalRangeStart;
  }
  
  public void setTemporalRangeStart(String temporalRangeStart) {
    this.temporalRangeStart = temporalRangeStart;
  }
  
  public String getTemporalRangeEnd() {
    return temporalRangeEnd;
  }
  
  public void setTemporalRangeEnd(String temporalRangeEnd) {
    this.temporalRangeEnd = temporalRangeEnd;
  }
  
  public Set<Lifezone> getLifezones() {
    return lifezones;
  }
  
  public void setLifezones(Set<Lifezone> lifezones) {
    this.lifezones = lifezones;
  }
  
  @JsonIgnore
  public DSID<String> getSubjectAsDSID() {
    return subject == null ? null : DSID.key(subjectDatasetKey, subject.getId());
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
        Objects.equals(extinct, that.extinct) &&
        Objects.equals(temporalRangeStart, that.temporalRangeStart) &&
        Objects.equals(temporalRangeEnd, that.temporalRangeEnd) &&
        Objects.equals(lifezones, that.lifezones);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), mode, name, status, extinct, temporalRangeStart, temporalRangeEnd, lifezones);
  }
  
  @Override
  public String toString() {
    return "Decision{" + getKey() + " " + mode + " on " + subject + '}';
  }
}

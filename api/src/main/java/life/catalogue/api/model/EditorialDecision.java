package life.catalogue.api.model;

import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A single, persisted editorial decision about a single name/taxon
 * within a given CoL sector.
 *
 * A decision can change a record or block it and all its descendants entirely.
 * If blocked all further configured changes are ignored.
 * Otherwise all non null values from status or name should be applied to the underlying subject.
 */
public class EditorialDecision extends DatasetScopedEntity<Integer> {
  private SimpleNameLink subject;
  private String originalSubjectId;
  private Integer subjectDatasetKey; // the datasetKey the subject belongs to, not the catalogue!
  private Mode mode;
  private Boolean keepOriginalName; // when true and mode=UPDATE, creates a synonym with the original name spelling
  private Name name;
  private TaxonomicStatus status;
  private Boolean extinct;
  private String temporalRangeStart;
  private String temporalRangeEnd;
  private Set<Environment> environments = EnumSet.noneOf(Environment.class);
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
     * Updates the subject using the configured name, status, environment and extinct flag
     * leaving NULL values unchanged.
     */
    UPDATE,
  
    /**
     * WARNING: NOT FULLY IMPLEMENTED YET !!!
     * Updates the subject and all its descendants using the configured status, environment and extinct flag
     * leaving NULL values unchanged.
     *
     * If configured, Name updates will be ignored!!!
     */
    UPDATE_RECURSIVE,

    /**
     * Ignores the subject, but still includes all it's descendants.
     */
    IGNORE
  }

  public EditorialDecision() {
  }

  public EditorialDecision(EditorialDecision other) {
    super(other);
    this.subject = SimpleNameLink.of(other.subject);
    this.originalSubjectId = other.originalSubjectId;
    this.subjectDatasetKey = other.subjectDatasetKey;
    this.mode = other.mode;
    this.keepOriginalName = other.keepOriginalName;
    this.name = other.name; // should we need to deep copy this too???
    this.status = other.status;
    this.extinct = other.extinct;
    this.temporalRangeStart = other.temporalRangeStart;
    this.temporalRangeEnd = other.temporalRangeEnd;
    this.environments = other.environments == null ? null : EnumSet.copyOf(other.environments);
    this.note = other.note;
  }

  public SimpleNameLink getSubject() {
    return subject;
  }
  
  public void setSubject(SimpleNameLink subject) {
    this.subject = subject;
  }

  public String getOriginalSubjectId() {
    return originalSubjectId;
  }

  public void setOriginalSubjectId(String originalSubjectId) {
    this.originalSubjectId = originalSubjectId;
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

  public Boolean isKeepOriginalName() {
    return keepOriginalName;
  }

  public void setKeepOriginalName(Boolean keepOriginalName) {
    this.keepOriginalName = keepOriginalName;
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
  
  public Set<Environment> getEnvironments() {
    return environments;
  }
  
  public void setEnvironments(Set<Environment> environments) {
    this.environments = environments;
  }
  
  @JsonIgnore
  public DSID<String> getSubjectAsDSID() {
    return subject == null ? null : DSID.of(subjectDatasetKey, subject.getId());
  }
  
  @JsonIgnore
  public SimpleDecision asSimpleDecision() {
    SimpleDecision sd = new SimpleDecision();
    sd.setId(getId());
    sd.setDatasetKey(getDatasetKey());
    sd.setMode(mode);
    return sd;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EditorialDecision that = (EditorialDecision) o;
    return Objects.equals(subject, that.subject) && Objects.equals(originalSubjectId, that.originalSubjectId) && Objects.equals(subjectDatasetKey, that.subjectDatasetKey) && mode == that.mode && Objects.equals(keepOriginalName, that.keepOriginalName) && Objects.equals(name, that.name) && status == that.status && Objects.equals(extinct, that.extinct) && Objects.equals(temporalRangeStart, that.temporalRangeStart) && Objects.equals(temporalRangeEnd, that.temporalRangeEnd) && Objects.equals(environments, that.environments) && Objects.equals(note, that.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subject, originalSubjectId, subjectDatasetKey, mode, keepOriginalName, name, status, extinct, temporalRangeStart, temporalRangeEnd, environments, note);
  }

  @Override
  public String toString() {
    return "Decision{" + getId() + " " + mode + " on " + subject + '}';
  }
}

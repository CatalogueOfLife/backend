package life.catalogue.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.Lifezone;
import life.catalogue.api.vocab.TaxonomicStatus;

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
  private String originalSubjectId;
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
  
  @JsonIgnore
  public SimpleDecision asSimpleDecision() {
    SimpleDecision sd = new SimpleDecision();
    sd.setKey(key);
    sd.setDatasetKey(datasetKey);
    sd.setMode(mode);
    return sd;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EditorialDecision that = (EditorialDecision) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(subject, that.subject) &&
        Objects.equals(originalSubjectId, that.originalSubjectId) &&
        Objects.equals(subjectDatasetKey, that.subjectDatasetKey) &&
        mode == that.mode &&
        Objects.equals(name, that.name) &&
        status == that.status &&
        Objects.equals(extinct, that.extinct) &&
        Objects.equals(temporalRangeStart, that.temporalRangeStart) &&
        Objects.equals(temporalRangeEnd, that.temporalRangeEnd) &&
        Objects.equals(lifezones, that.lifezones) &&
        Objects.equals(note, that.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, datasetKey, subject, originalSubjectId, subjectDatasetKey, mode, name, status, extinct, temporalRangeStart, temporalRangeEnd, lifezones, note);
  }

  @Override
  public String toString() {
    return "Decision{" + getKey() + " " + mode + " on " + subject + '}';
  }
}

package life.catalogue.api.model;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A taxonomic sector definition within a dataset that is used to assemble the Catalogue of Life.
 * Sectors will also serve to show the taxonomic coverage in the CoL portal.
 * The subject of the sector is the root taxon in the original source dataset.
 * The target is the matching taxon in the assembled catalogue,
 * the subject should be replacing in ATTACH mode which is the default.
 * In MERGE mode the subject taxon itself should be skipped and only its descendants be included if they do
 * not already exist.
 *
 * A sector can be really small and the subject even be a species, but usually it is some higher taxon.
 */
public class Sector extends DatasetScopedEntity<Integer> {
  private SimpleNameLink target;
  private Integer subjectDatasetKey; // the datasetKey the subject belongs to, not the catalogue!
  private SimpleNameLink subject;
  private String originalSubjectId;
  private Rank placeholderRank; // optional placeholder rank for the subject, i.e. children of higher ranks than this will be ignored
  private Mode mode = Sector.Mode.ATTACH;
  private Integer priority; // the lower the higher prio. NULL sorts last
  private Integer syncAttempt;
  private Integer datasetAttempt;
  // defaults to apply to usages during a sync
  private NomCode code;
  // settings what to sync. Inclusive set unless called "exclusion"
  private Set<Rank> ranks;
  private Set<EntityType> entities;
  private Set<NameType> nameTypes;
  private Set<NomStatus> nameStatusExclusion;
  private Boolean extinctFilter = null; // true only syncs extinct, false only extant, null all
  private boolean copyAccordingTo = false;
  private boolean removeOrdinals = false;
  private boolean createImplicitNames = true;
  // other
  private String note;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer size;

  public static enum Mode {
    /**
     * Attach the entire subject and its descendants under its target parent.
     */
    ATTACH,

    /**
     * Unite all descendants of subject under the target taxon, but exclude the subject taxon itself.
     * Does not check for duplicates and create the same name twice if configured to.
     */
    UNION,

    /**
     * Merge all descendants of subject under the target taxon, but exclude the subject taxon itself.
     * This operation will only create new names and try to avoid the creation of duplicates automatically.
     */
    MERGE
  }

  public Sector() {
  }

  /**
   * Deep copy constructor
   */
  public Sector(Sector other) {
    super(other);
    this.target = other.target == null ? null : SimpleNameLink.of(other.target);
    this.subjectDatasetKey = other.subjectDatasetKey;
    this.subject = other.subject == null ? null : SimpleNameLink.of(other.subject);
    this.originalSubjectId = other.originalSubjectId;
    this.mode = other.mode;
    this.priority = other.priority;
    this.syncAttempt = other.syncAttempt;
    this.datasetAttempt = other.datasetAttempt;
    this.code = other.code;
    this.placeholderRank = other.placeholderRank;
    this.ranks = other.ranks == null ? null : EnumSet.copyOf(other.ranks);
    this.entities = other.entities == null ? null : EnumSet.copyOf(other.entities);
    this.nameTypes = other.nameTypes == null ? null : EnumSet.copyOf(other.nameTypes);
    this.nameStatusExclusion = other.nameStatusExclusion == null ? null : EnumSet.copyOf(other.nameStatusExclusion);
    this.extinctFilter = other.extinctFilter;
    this.createImplicitNames = other.createImplicitNames;
    this.note = other.note;
    this.size = other.size;
  }

  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }
  
  public SimpleNameLink getSubject() {
    return subject;
  }

  /**
   * NPE safe convenience getter to yield the subjects id or null.
   */
  @JsonIgnore
  public String getSubjectID() {
    return subject == null ? null : subject.getId();
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

  @JsonIgnore
  public DSID<String> getSubjectAsDSID() {
    return subject == null ? null : DSID.of(subjectDatasetKey, subject.getId());
  }
  
  @JsonIgnore
  public DSID<String> getTargetAsDSID() {
    return target == null ? null : DSID.of(getDatasetKey(), target.getId());
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }

  public void addNote(String note) {
    if (!StringUtils.isBlank(note)) {
      if (this.note == null) {
        this.note = note.trim();
      } else {
        this.note = this.note + "; " + note.trim();
      }
    }
  }

  public Mode getMode() {
    return mode;
  }
  
  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  /**
   * @return the last successful sync attempt that created the current data
   */
  public Integer getSyncAttempt() {
    return syncAttempt;
  }

  public void setSyncAttempt(Integer syncAttempt) {
    this.syncAttempt = syncAttempt;
  }

  public Integer getDatasetAttempt() {
    return datasetAttempt;
  }

  public void setDatasetAttempt(Integer datasetAttempt) {
    this.datasetAttempt = datasetAttempt;
  }

  public NomCode getCode() {
    return code;
  }
  
  public void setCode(NomCode code) {
    this.code = code;
  }
  
  /**
   * The attachment point in the CoL tree, i.e. the CoL parent taxon for the sector root
   */
  public SimpleNameLink getTarget() {
    return target;
  }

  /**
   * NPE safe convenience getter to yield the targets id or null.
   */
  @JsonIgnore
  public String getTargetID() {
    return target == null ? null : target.getId();
  }

  public void setTarget(SimpleNameLink target) {
    this.target = target;
  }

  public Rank getPlaceholderRank() {
    return placeholderRank;
  }

  public void setPlaceholderRank(Rank placeholderRank) {
    this.placeholderRank = placeholderRank;
  }

  public Set<Rank> getRanks() {
    return ranks;
  }

  public void setRanks(Set<Rank> ranks) {
    this.ranks = ranks;
  }

  public Set<EntityType> getEntities() {
    return entities;
  }

  public void setEntities(Set<EntityType> entities) {
    this.entities = entities;
  }

  public Set<NameType> getNameTypes() {
    return nameTypes;
  }

  public void setNameTypes(Set<NameType> nameTypes) {
    this.nameTypes = nameTypes;
  }

  public Set<NomStatus> getNameStatusExclusion() {
    return nameStatusExclusion;
  }

  public void setNameStatusExclusion(Set<NomStatus> nameStatusExclusion) {
    this.nameStatusExclusion = nameStatusExclusion;
  }

  public Boolean getExtinctFilter() {
    return extinctFilter;
  }

  public void setExtinctFilter(Boolean extinctFilter) {
    this.extinctFilter = extinctFilter;
  }

  public boolean isCreateImplicitNames() {
    return createImplicitNames;
  }

  public void setCreateImplicitNames(boolean createImplicitNames) {
    this.createImplicitNames = createImplicitNames;
  }

  public boolean isCopyAccordingTo() {
    return copyAccordingTo;
  }

  public void setCopyAccordingTo(boolean copyAccordingTo) {
    this.copyAccordingTo = copyAccordingTo;
  }

  public boolean isRemoveOrdinals() {
    return removeOrdinals;
  }

  public void setRemoveOrdinals(boolean removeOrdinals) {
    this.removeOrdinals = removeOrdinals;
  }

  public Integer getSize() {
    return size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Sector)) return false;
    if (!super.equals(o)) return false;
    Sector sector = (Sector) o;
    return Objects.equals(target, sector.target)
           && Objects.equals(subjectDatasetKey, sector.subjectDatasetKey)
           && Objects.equals(subject, sector.subject)
           && Objects.equals(originalSubjectId, sector.originalSubjectId)
           && placeholderRank == sector.placeholderRank
           && mode == sector.mode
           && Objects.equals(priority, sector.priority)
           && Objects.equals(syncAttempt, sector.syncAttempt)
           && Objects.equals(datasetAttempt, sector.datasetAttempt)
           && code == sector.code
           && createImplicitNames == sector.createImplicitNames
           && Objects.equals(ranks, sector.ranks)
           && Objects.equals(entities, sector.entities)
           && Objects.equals(nameTypes, sector.nameTypes)
           && Objects.equals(nameStatusExclusion, sector.nameStatusExclusion)
           && Objects.equals(extinctFilter, sector.extinctFilter)
           && Objects.equals(note, sector.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), target, subjectDatasetKey, subject, originalSubjectId, placeholderRank, mode, priority, syncAttempt, datasetAttempt, code, createImplicitNames, ranks, entities, nameTypes, nameStatusExclusion, extinctFilter, note);
  }

  @Override
  public String toString() {
    return "Sector{" + getId() +
        ", datasetKey=" + getDatasetKey() +
        ", mode=" + mode +
        ", subjectDatasetKey=" + getSubjectDatasetKey() +
        ", subject=" + getSubject() +
        '}';
  }

  public static Sector.Builder newBuilder() {
    return new Sector.Builder();
  }

  public static final class Builder {
    private LocalDateTime created;
    private Integer createdBy;
    private LocalDateTime modified;
    private Integer modifiedBy;
    private Integer datasetKey;
    private Integer id;
    private SimpleNameLink target;
    private Integer subjectDatasetKey;
    private SimpleNameLink subject;
    private String originalSubjectId;
    private Rank placeholderRank;
    private Mode mode;
    private Integer priority;
    private Integer syncAttempt;
    private Integer datasetAttempt;
    private NomCode code;
    private Set<Rank> ranks;
    private Set<EntityType> entities;
    private Set<NameType> nameTypes;
    private Set<NomStatus> nameStatusExclusion;
    private boolean copyAccordingTo;
    private boolean removeOrdinals;
    private boolean createImplicitNames;
    private String note;
    private Integer size;

    private Builder() {
    }

    public Builder created(LocalDateTime created) {
      this.created = created;
      return this;
    }

    public Builder createdBy(Integer createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder modified(LocalDateTime modified) {
      this.modified = modified;
      return this;
    }

    public Builder modifiedBy(Integer modifiedBy) {
      this.modifiedBy = modifiedBy;
      return this;
    }

    public Builder datasetKey(Integer datasetKey) {
      this.datasetKey = datasetKey;
      return this;
    }

    public Builder id(Integer id) {
      this.id = id;
      return this;
    }

    public Builder target(SimpleNameLink target) {
      this.target = target;
      return this;
    }

    public Builder subjectDatasetKey(Integer subjectDatasetKey) {
      this.subjectDatasetKey = subjectDatasetKey;
      return this;
    }

    public Builder subject(SimpleNameLink subject) {
      this.subject = subject;
      return this;
    }

    public Builder originalSubjectId(String originalSubjectId) {
      this.originalSubjectId = originalSubjectId;
      return this;
    }

    public Builder placeholderRank(Rank placeholderRank) {
      this.placeholderRank = placeholderRank;
      return this;
    }

    public Builder mode(Mode mode) {
      this.mode = mode;
      return this;
    }

    public Builder priority(Integer priority) {
      this.priority = priority;
      return this;
    }

    public Builder syncAttempt(Integer syncAttempt) {
      this.syncAttempt = syncAttempt;
      return this;
    }

    public Builder datasetAttempt(Integer datasetAttempt) {
      this.datasetAttempt = datasetAttempt;
      return this;
    }

    public Builder code(NomCode code) {
      this.code = code;
      return this;
    }

    public Builder ranks(Set<Rank> ranks) {
      this.ranks = ranks;
      return this;
    }

    public Builder entities(Set<EntityType> entities) {
      this.entities = entities;
      return this;
    }

    public Builder nameTypes(Set<NameType> nameTypes) {
      this.nameTypes = nameTypes;
      return this;
    }

    public Builder nameStatusExclusion(Set<NomStatus> nameStatusExclusion) {
      this.nameStatusExclusion = nameStatusExclusion;
      return this;
    }

    public Builder copyAccordingTo(boolean copyAccordingTo) {
      this.copyAccordingTo = copyAccordingTo;
      return this;
    }

    public Builder removeOrdinals(boolean removeOrdinals) {
      this.removeOrdinals = removeOrdinals;
      return this;
    }

    public Builder createImplicitNames(boolean createImplicitNames) {
      this.createImplicitNames = createImplicitNames;
      return this;
    }

    public Builder note(String note) {
      this.note = note;
      return this;
    }

    public Builder size(Integer size) {
      this.size = size;
      return this;
    }

    public Sector build() {
      Sector sector = new Sector();
      sector.setCreated(created);
      sector.setCreatedBy(createdBy);
      sector.setModified(modified);
      sector.setModifiedBy(modifiedBy);
      sector.setDatasetKey(datasetKey);
      sector.setId(id);
      sector.setTarget(target);
      sector.setSubjectDatasetKey(subjectDatasetKey);
      sector.setSubject(subject);
      sector.setOriginalSubjectId(originalSubjectId);
      sector.setPlaceholderRank(placeholderRank);
      sector.setMode(mode);
      sector.setPriority(priority);
      sector.setSyncAttempt(syncAttempt);
      sector.setDatasetAttempt(datasetAttempt);
      sector.setCode(code);
      sector.setRanks(ranks);
      sector.setEntities(entities);
      sector.setNameTypes(nameTypes);
      sector.setNameStatusExclusion(nameStatusExclusion);
      sector.setCopyAccordingTo(copyAccordingTo);
      sector.setRemoveOrdinals(removeOrdinals);
      sector.setCreateImplicitNames(createImplicitNames);
      sector.setNote(note);
      sector.size = this.size;
      return sector;
    }
  }
}

package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.vocab.EntityType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

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
  private SimpleName target;
  private Integer subjectDatasetKey; // the datasetKey the subject belongs to, not the catalogue!
  private SimpleName subject;
  private String originalSubjectId;
  private Mode mode = Sector.Mode.ATTACH;
  private Integer syncAttempt;
  private Integer datasetImportAttempt;
  private NomCode code;
  private Rank placeholderRank;
  private Set<Rank> ranks;
  private Set<EntityType> entities;
  private String note;
  
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

  public Sector(Sector other) {
    super(other);
    this.target = new SimpleName(other.target);
    this.subjectDatasetKey = other.subjectDatasetKey;
    this.subject = new SimpleName(other.subject);
    this.originalSubjectId = other.originalSubjectId;
    this.mode = other.mode;
    this.syncAttempt = other.syncAttempt;
    this.datasetImportAttempt = other.datasetImportAttempt;
    this.code = other.code;
    this.placeholderRank = other.placeholderRank;
    this.ranks = other.ranks == null ? null : EnumSet.copyOf(other.ranks);
    this.entities = other.entities == null ? null : EnumSet.copyOf(other.entities);
    this.note = other.note;
  }

  /**
   * @return the id in the old legacy property "key"
   */
  @JsonProperty("key")
  public Integer getKeyLEGACAY(){
    return getId();
  }

  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
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
  
  public Mode getMode() {
    return mode;
  }
  
  public void setMode(Mode mode) {
    this.mode = mode;
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

  public Integer getDatasetImportAttempt() {
    return datasetImportAttempt;
  }

  public void setDatasetImportAttempt(Integer datasetImportAttempt) {
    this.datasetImportAttempt = datasetImportAttempt;
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
  public SimpleName getTarget() {
    return target;
  }
  
  public void setTarget(SimpleName target) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Sector sector = (Sector) o;
    return Objects.equals(target, sector.target) &&
      Objects.equals(subjectDatasetKey, sector.subjectDatasetKey) &&
      Objects.equals(subject, sector.subject) &&
      Objects.equals(originalSubjectId, sector.originalSubjectId) &&
      mode == sector.mode &&
      Objects.equals(syncAttempt, sector.syncAttempt) &&
      Objects.equals(datasetImportAttempt, sector.datasetImportAttempt) &&
      code == sector.code &&
      placeholderRank == sector.placeholderRank &&
      Objects.equals(ranks, sector.ranks) &&
      Objects.equals(entities, sector.entities) &&
      Objects.equals(note, sector.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), target, subjectDatasetKey, subject, originalSubjectId, mode, syncAttempt, datasetImportAttempt, code, placeholderRank, ranks, entities, note);
  }

  @Override
  public String toString() {
    return "Sector{" + getId() +
        ", datasetKey=" + getDatasetKey() +
        ", mode=" + mode +
        ", code=" + code +
        ", subjectDatasetKey=" + getSubjectDatasetKey() +
        ", subject=" + getSubject() +
        '}';
  }
}

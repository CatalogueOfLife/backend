package life.catalogue.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.gbif.nameparser.api.NomCode;

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
public class Sector extends DataEntity<Integer> implements DatasetScoped {
  private Integer key;
  private Integer datasetKey; // the catalogues datasetKey
  private SimpleName target;
  private Integer subjectDatasetKey; // the datasetKey the subject belongs to, not the catalogue!
  private SimpleName subject;
  private Mode mode = Sector.Mode.ATTACH;
  private NomCode code;
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
  
  @JsonIgnore
  public DSID<String> getSubjectAsDSID() {
    return subject == null ? null : DSID.key(subjectDatasetKey, subject.getId());
  }
  
  @JsonIgnore
  public DSID<String> getTargetAsDSID() {
    return target == null ? null : DSID.key(datasetKey, target.getId());
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Sector sector = (Sector) o;
    return Objects.equals(key, sector.key) &&
        Objects.equals(datasetKey, sector.datasetKey) &&
        Objects.equals(target, sector.target) &&
        Objects.equals(subjectDatasetKey, sector.subjectDatasetKey) &&
        Objects.equals(subject, sector.subject) &&
        mode == sector.mode &&
        code == sector.code &&
        Objects.equals(note, sector.note);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, datasetKey, target, subjectDatasetKey, subject, mode, code, note);
  }
  
  @Override
  public String toString() {
    return "Sector{" + getKey() +
        ", datasetKey=" + getDatasetKey() +
        ", mode=" + mode +
        ", code=" + code +
        ", subjectDatasetKey=" + getSubjectDatasetKey() +
        ", subject=" + getSubject() +
        '}';
  }
}

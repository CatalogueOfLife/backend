package life.catalogue.api.search;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.nameparser.api.NomCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.Min;
import javax.ws.rs.QueryParam;

import com.google.common.base.Preconditions;

public class DatasetSearchRequest {
  
  public enum SortBy {
    KEY,
    ALIAS,
    TITLE,
    AUTHORS,
    RELEVANCE,
    CREATED,
    MODIFIED,
    SIZE
  }
  
  @QueryParam("q")
  private String q;
  
  @QueryParam("code")
  private NomCode code;

  /**
   * Filters by private flag.
   */
  @QueryParam("private")
  private Boolean privat;

  /**
   * Filters release datasets by their parent project.
   * Automatically also restricts datasets to origin=released
   */
  @QueryParam("releasedFrom")
  private Integer releasedFrom;

  /**
   * Filters datasets to only list those that contribute as a source dataset with at least one sector to a given project.
   */
  @QueryParam("contributesTo")
  private Integer contributesTo;

  /**
   * Filters datasets by having at least one sector with a given source datasetKey.
   * Sectors only exist for managed but also released datasets.
   */
  @QueryParam("hasSourceDataset")
  private Integer hasSourceDataset;

  /**
   * Filters datasets by their gbif Publisher UUID.
   */
  @QueryParam("gbifPublisherKey")
  private UUID gbifPublisherKey;

  /**
   * Filters datasets by having the given editor key authorized.
   */
  @QueryParam("editor")
  private Integer editor;

  /**
   * Filters datasets by the user key that has last modified/created the dataset.
   */
  @QueryParam("modifiedBy")
  private Integer modifiedBy;

  @QueryParam("origin")
  private List<DatasetOrigin> origin;

  @QueryParam("type")
  private List<DatasetType> type;

  @QueryParam("license")
  private List<License> license;

  @QueryParam("modified")
  private LocalDate modified;
  
  @QueryParam("created")
  private LocalDate created;
  
  @QueryParam("issued")
  private FuzzyDate issued;

  @Min(0)
  @QueryParam("minSize")
  private Integer minSize;

  @QueryParam("sortBy")
  private SortBy sortBy;
  
  @QueryParam("reverse")
  private boolean reverse = false;
  
  public static DatasetSearchRequest byQuery(String query) {
    DatasetSearchRequest q = new DatasetSearchRequest();
    q.q = query;
    return q;
  }
  
  public String getQ() {
    return q;
  }
  
  public void setQ(String q) {
    this.q = q;
  }

  public Boolean isPrivat() {
    return privat;
  }

  public void setPrivat(Boolean privat) {
    this.privat = privat;
  }

  public Integer getMinSize() {
    return minSize;
  }

  public void setMinSize(Integer minSize) {
    this.minSize = minSize;
  }

  public NomCode getCode() {
    return code;
  }
  
  public void setCode(NomCode code) {
    this.code = code;
  }
  
  public Integer getContributesTo() {
    return contributesTo;
  }
  
  public void setContributesTo(Integer contributesTo) {
    this.contributesTo = contributesTo;
  }

  public Integer getReleasedFrom() {
    return releasedFrom;
  }

  public void setReleasedFrom(Integer releasedFrom) {
    this.releasedFrom = releasedFrom;
  }

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  public Integer getEditor() {
    return editor;
  }

  public void setEditor(Integer editor) {
    this.editor = editor;
  }

  public Integer getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(Integer modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Integer getHasSourceDataset() {
    return hasSourceDataset;
  }

  public void setHasSourceDataset(Integer hasSourceDataset) {
    this.hasSourceDataset = hasSourceDataset;
  }

  public List<DatasetType> getType() {
    return type;
  }

  public void setType(List<DatasetType> type) {
    this.type = type;
  }

  public List<License> getLicense() {
    return license;
  }

  public void setLicense(List<License> license) {
    this.license = license;
  }

  public List<DatasetOrigin> getOrigin() {
    return origin;
  }
  
  public void setOrigin(List<DatasetOrigin> origin) {
    this.origin = origin;
  }
  
  public LocalDate getModified() {
    return modified;
  }
  
  public void setModified(LocalDate modified) {
    this.modified = modified;
  }
  
  public FuzzyDate getIssued() {
    return issued;
  }
  
  public void setIssued(FuzzyDate issued) {
    this.issued = issued;
  }
  
  public LocalDate getCreated() {
    return created;
  }
  
  public void setCreated(LocalDate created) {
    this.created = created;
  }
  
  public SortBy getSortBy() {
    return sortBy;
  }
  
  public void setSortBy(SortBy sortBy) {
    this.sortBy = Preconditions.checkNotNull(sortBy);
  }
  
  public boolean isReverse() {
    return reverse;
  }
  
  public void setReverse(boolean reverse) {
    this.reverse = reverse;
  }

  /**
   * Chronological order, first created comes first
   */
  public void setSortByReverseCreated() {
    sortBy = SortBy.CREATED;
    reverse = true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DatasetSearchRequest)) return false;
    DatasetSearchRequest that = (DatasetSearchRequest) o;
    return reverse == that.reverse
           && Objects.equals(q, that.q)
           && code == that.code
           && Objects.equals(privat, that.privat)
           && Objects.equals(releasedFrom, that.releasedFrom)
           && Objects.equals(contributesTo, that.contributesTo)
           && Objects.equals(hasSourceDataset, that.hasSourceDataset)
           && Objects.equals(gbifPublisherKey, that.gbifPublisherKey)
           && Objects.equals(editor, that.editor)
           && Objects.equals(modifiedBy, that.modifiedBy)
           && Objects.equals(origin, that.origin)
           && Objects.equals(type, that.type)
           && Objects.equals(license, that.license)
           && Objects.equals(modified, that.modified)
           && Objects.equals(created, that.created)
           && Objects.equals(issued, that.issued)
           && Objects.equals(minSize, that.minSize)
           && sortBy == that.sortBy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, code, privat, releasedFrom, contributesTo, hasSourceDataset, gbifPublisherKey, editor, modifiedBy, origin, type, license, modified, created, issued, minSize, sortBy, reverse);
  }
}

package life.catalogue.api.search;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;

import com.google.common.base.Preconditions;

public class DatasetSearchRequest {
  
  public enum SortBy {
    KEY,
    ALIAS,
    TITLE,
    CREATOR,
    RELEVANCE,
    CREATED,
    MODIFIED,
    IMPORTED,
    LAST_IMPORT_ATTEMPT,
    SIZE
  }
  
  @QueryParam("q")
  private String q;

  @QueryParam("alias")
  private String alias;

  @QueryParam("code")
  private NomCode code;

  @Deprecated
  @QueryParam("codeIsNull")
  private boolean codeIsNull;

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
   * Filters datasets that either have or do not have a GBIF key.
   */
  @QueryParam("hasGbifKey")
  private Boolean hasGbifKey;

  /**
   * Filters datasets by their gbif dataset UUID.
   */
  @QueryParam("gbifKey")
  private UUID gbifKey;

  /**
   * Filters datasets by their gbif Publisher UUID.
   */
  @QueryParam("gbifPublisherKey")
  private UUID gbifPublisherKey;

  /**
   * Filters datasets that have no sector in the given projects dataset key
   */
  @QueryParam("withoutSectorInProject")
  private Integer withoutSectorInProject;

  /**
   * Filters datasets by their last import state
   */
  @QueryParam("lastImportState")
  private ImportState lastImportState;

  /**
   * Filters datasets by having the given editor key authorized.
   */
  @QueryParam("editor")
  private Integer editor;

  /**
   * Filters datasets by having the given reviewer key authorized.
   */
  @QueryParam("reviewer")
  private Integer reviewer;

  @QueryParam("origin")
  private List<DatasetOrigin> origin;

  @QueryParam("type")
  private List<DatasetType> type;

  @QueryParam("license")
  private List<License> license;

  @QueryParam("rowType")
  private List<Term> rowType;

  @QueryParam("modified")
  private LocalDate modified;

  @QueryParam("modifiedBefore")
  private LocalDate modifiedBefore;

  /**
   * Filters datasets by the user key that has last modified the dataset.
   */
  @QueryParam("modifiedBy")
  private Integer modifiedBy;

  @QueryParam("created")
  private LocalDate created;

  @QueryParam("createdBefore")
  private LocalDate createdBefore;

  /**
   * Filters datasets by the user key that has created the dataset.
   */
  @QueryParam("createdBy")
  private Integer createdBy;

  @QueryParam("issued")
  private FuzzyDate issued;

  @QueryParam("issuedBefore")
  private FuzzyDate issuedBefore;

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

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
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

  public boolean isCodeIsNull() {
    return codeIsNull;
  }

  public void setCodeIsNull(boolean codeIsNull) {
    this.codeIsNull = codeIsNull;
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

  public Boolean getHasGbifKey() {
    return hasGbifKey;
  }

  public void setHasGbifKey(Boolean hasGbifKey) {
    this.hasGbifKey = hasGbifKey;
  }

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  public Integer getWithoutSectorInProject() {
    return withoutSectorInProject;
  }

  public void setWithoutSectorInProject(Integer withoutSectorInProject) {
    this.withoutSectorInProject = withoutSectorInProject;
  }

  public ImportState getLastImportState() {
    return lastImportState;
  }

  public void setLastImportState(ImportState lastImportState) {
    this.lastImportState = lastImportState;
  }

  public Integer getEditor() {
    return editor;
  }

  public void setEditor(Integer editor) {
    this.editor = editor;
  }

  public Integer getReviewer() {
    return reviewer;
  }

  public void setReviewer(Integer reviewer) {
    this.reviewer = reviewer;
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

  public List<Term> getRowType() {
    return rowType;
  }

  public void setRowType(List<Term> rowType) {
    this.rowType = rowType;
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

  public LocalDate getModifiedBefore() {
    return modifiedBefore;
  }

  public void setModifiedBefore(LocalDate modifiedBefore) {
    this.modifiedBefore = modifiedBefore;
  }

  public FuzzyDate getIssued() {
    return issued;
  }

  public void setIssued(FuzzyDate issued) {
    this.issued = issued;
  }

  public FuzzyDate getIssuedBefore() {
    return issuedBefore;
  }

  public void setIssuedBefore(FuzzyDate issuedBefore) {
    this.issuedBefore = issuedBefore;
  }

  public LocalDate getCreated() {
    return created;
  }

  public void setCreated(LocalDate created) {
    this.created = created;
  }

  public LocalDate getCreatedBefore() {
    return createdBefore;
  }

  public void setCreatedBefore(LocalDate createdBefore) {
    this.createdBefore = createdBefore;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
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
    return codeIsNull == that.codeIsNull && reverse == that.reverse && Objects.equals(q, that.q) && Objects.equals(alias, that.alias) && code == that.code && Objects.equals(privat, that.privat) && Objects.equals(releasedFrom, that.releasedFrom) && Objects.equals(contributesTo, that.contributesTo) && Objects.equals(hasSourceDataset, that.hasSourceDataset) && Objects.equals(hasGbifKey, that.hasGbifKey) && Objects.equals(gbifKey, that.gbifKey) && Objects.equals(gbifPublisherKey, that.gbifPublisherKey) && Objects.equals(withoutSectorInProject, that.withoutSectorInProject) && lastImportState == that.lastImportState && Objects.equals(editor, that.editor) && Objects.equals(reviewer, that.reviewer) && Objects.equals(origin, that.origin) && Objects.equals(type, that.type) && Objects.equals(license, that.license) && Objects.equals(rowType, that.rowType) && Objects.equals(modified, that.modified) && Objects.equals(modifiedBefore, that.modifiedBefore) && Objects.equals(modifiedBy, that.modifiedBy) && Objects.equals(created, that.created) && Objects.equals(createdBefore, that.createdBefore) && Objects.equals(createdBy, that.createdBy) && Objects.equals(issued, that.issued) && Objects.equals(issuedBefore, that.issuedBefore) && Objects.equals(minSize, that.minSize) && sortBy == that.sortBy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, alias, code, codeIsNull, privat, releasedFrom, contributesTo, hasSourceDataset, hasGbifKey, gbifKey, gbifPublisherKey, withoutSectorInProject, lastImportState, editor, reviewer, origin, type, license, rowType, modified, modifiedBefore, modifiedBy, created, createdBefore, createdBy, issued, issuedBefore, minSize, sortBy, reverse);
  }
}

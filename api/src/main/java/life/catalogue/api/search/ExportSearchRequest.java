package life.catalogue.api.search;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.Set;

import jakarta.ws.rs.QueryParam;

/**
 * Watch out - null values mean different things.
 * For most properties NULL means I don't care - match any.
 * For some the filter only selects actual null values which is indicated in the fields descriptions
 */
public class ExportSearchRequest {
  @QueryParam("datasetKey")
  private Integer datasetKey;

  @QueryParam("createdBy")
  private Integer createdBy;

  @QueryParam("status")
  private Set<JobStatus> status;

  @QueryParam("format")
  private DataFormat format;

  @QueryParam("taxonID")
  private String taxonID; // NULL requires taxonID to be null in the export

  @QueryParam("minRank")
  private Rank minRank; // NULL requires minRank to be null in the export

  @QueryParam("synonyms")
  private Boolean synonyms;

  @QueryParam("bareNames")
  private Boolean bareNames;

  @QueryParam("excel")
  private Boolean excel;

  @QueryParam("extended")
  private Boolean extended;

  @QueryParam("extinct")
  private Boolean extinct;// NULL requires extinct to be null in the export

  /**
   * Matches complete exports of a dataset not in Excel which have finished successfully and regardless of their format
   * @param datasetKey
   */
  public static ExportSearchRequest fullDataset(int datasetKey){
    ExportSearchRequest req = new ExportSearchRequest();
    req.setDatasetKey(datasetKey);
    req.setSynonyms(true);

    req.setExtinct(null);
    req.setExcel(false);
    req.setMinRank(null);
    req.setTaxonID(null);
    req.setCreatedBy(null);
    req.setSingleStatus(JobStatus.FINISHED);
    return req;
  }

  public ExportSearchRequest() {

  }

  public ExportSearchRequest(ExportRequest req) {
    datasetKey = req.getDatasetKey();
    format = req.getFormat();
    if (req.getRoot() != null) {
      taxonID = req.getRoot().getId();
    }
    minRank = req.getMinRank();
    excel = req.isExcel();
    bareNames = req.isBareNames();
    extended = req.isExtended();
    synonyms = req.isSynonyms();
    extinct = req.getExtinct();
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  public Set<JobStatus> getStatus() {
    return status;
  }

  public void setStatus(Set<JobStatus> status) {
    this.status = status;
  }

  public void setSingleStatus(JobStatus status) {
    this.status = Set.of(status);
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  public String getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }

  public Rank getMinRank() {
    return minRank;
  }

  public void setMinRank(Rank minRank) {
    this.minRank = minRank;
  }

  public Boolean getBareNames() {
    return bareNames;
  }

  public void setBareNames(Boolean bareNames) {
    this.bareNames = bareNames;
  }

  public Boolean getExtended() {
    return extended;
  }

  public void setExtended(Boolean extended) {
    this.extended = extended;
  }

  public Boolean getExcel() {
    return excel;
  }

  public void setExcel(Boolean excel) {
    this.excel = excel;
  }

  public Boolean getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(Boolean synonyms) {
    this.synonyms = synonyms;
  }

  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExportSearchRequest that = (ExportSearchRequest) o;
    return Objects.equals(datasetKey, that.datasetKey) && Objects.equals(createdBy, that.createdBy) && Objects.equals(status, that.status) && format == that.format && Objects.equals(taxonID, that.taxonID) && minRank == that.minRank && Objects.equals(synonyms, that.synonyms) && Objects.equals(bareNames, that.bareNames) && Objects.equals(excel, that.excel) && Objects.equals(extended, that.extended) && Objects.equals(extinct, that.extinct);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, createdBy, status, format, taxonID, minRank, synonyms, bareNames, excel, extended, extinct);
  }
}

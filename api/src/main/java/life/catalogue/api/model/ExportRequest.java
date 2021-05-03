package life.catalogue.api.model;

import life.catalogue.api.vocab.DataFormat;
import org.gbif.nameparser.api.Rank;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public class ExportRequest {
  private Integer datasetKey;
  private DataFormat format;
  private boolean excel;
  private String taxonID;
  private boolean synonyms = true;
  private Rank minRank;

  public ExportRequest() {
  }

  public ExportRequest(int datasetKey, DataFormat format) {
    this.datasetKey = datasetKey;
    this.format = format;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  public boolean isExcel() {
    return excel;
  }

  public void setExcel(boolean excel) {
    this.excel = excel;
  }

  public String getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }

  public boolean isSynonyms() {
    return synonyms;
  }

  public void setSynonyms(boolean synonyms) {
    this.synonyms = synonyms;
  }

  public Rank getMinRank() {
    return minRank;
  }

  public void setMinRank(Rank minRank) {
    this.minRank = minRank;
  }

  /**
   * @return true if any filter has been used apart from the mandatory datasetKey
   */
  public boolean hasFilter() {
    return !synonyms || taxonID!=null || minRank!=null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExportRequest)) return false;
    ExportRequest that = (ExportRequest) o;
    return datasetKey == that.datasetKey && excel == that.excel && synonyms == that.synonyms && format == that.format && Objects.equals(taxonID, that.taxonID) && minRank == that.minRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, format, excel, taxonID, synonyms, minRank);
  }
}

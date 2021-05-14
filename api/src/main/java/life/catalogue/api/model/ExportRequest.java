package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;

import org.gbif.nameparser.api.Rank;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public class ExportRequest {
  private Integer datasetKey;
  private DataFormat format;
  private boolean excel;
  private SimpleName root;
  private boolean synonyms = true;
  private Rank minRank;
  private boolean force; // this makes sure we run a new export

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

  public SimpleName getRoot() {
    return root;
  }

  public void setRoot(SimpleName root) {
    this.root = root;
  }

  @JsonIgnore
  public String getTaxonID() {
    return root == null ? null : root.getId();
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

  public boolean isForce() {
    return force;
  }

  public void setForce(boolean force) {
    this.force = force;
  }

  /**
   * @return true if any filter has been used apart from the mandatory datasetKey & format
   */
  public boolean hasFilter() {
    return !synonyms || root!=null || minRank!=null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExportRequest)) return false;
    ExportRequest that = (ExportRequest) o;
    return excel == that.excel && synonyms == that.synonyms && force == that.force && Objects.equals(datasetKey, that.datasetKey) && format == that.format && Objects.equals(root, that.root) && minRank == that.minRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, format, excel, root, synonyms, minRank, force);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(format + " export of " + datasetKey);
    sb.append(" [excel=").append(excel)
      .append(", synonyms=").append(synonyms);
    if (minRank != null) {
      sb.append(", minRank=").append(minRank);
    }
    if (root != null) {
      sb.append(", root=").append(root.getId());
    }
    sb.append("]");
    return sb.toString();
  }
}

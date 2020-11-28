package life.catalogue.exporter;

import life.catalogue.api.vocab.DataFormat;
import org.gbif.nameparser.api.Rank;

import javax.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Set;

public class ExportRequest {
  private int datasetKey;
  @NotNull
  private DataFormat format = DataFormat.COLDP;
  private String startID;
  private Set<String> exclusions;
  private boolean synonyms = true;
  private Rank minRank;
  private int userKey;

  public ExportRequest() {
  }

  public ExportRequest(int datasetKey, DataFormat format) {
    this.datasetKey = datasetKey;
    this.format = format;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  public String getStartID() {
    return startID;
  }

  public void setStartID(String startID) {
    this.startID = startID;
  }

  public Set<String> getExclusions() {
    return exclusions;
  }

  public void setExclusions(Set<String> exclusions) {
    this.exclusions = exclusions;
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

  public int getUserKey() {
    return userKey;
  }

  public void setUserKey(int userKey) {
    this.userKey = userKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExportRequest)) return false;
    ExportRequest that = (ExportRequest) o;
    return datasetKey == that.datasetKey &&
      synonyms == that.synonyms &&
      userKey == that.userKey &&
      format == that.format &&
      Objects.equals(startID, that.startID) &&
      Objects.equals(exclusions, that.exclusions) &&
      minRank == that.minRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, format, startID, exclusions, synonyms, minRank, userKey);
  }
}

package life.catalogue.api.model;

import life.catalogue.api.vocab.IdReportType;

import java.util.Objects;

public class IdReportEntry {
  private int datasetKey; // the datasetKey for the release being reported
  private IdReportType type;
  private int id;

  public IdReportEntry() {
  }

  public IdReportEntry(int datasetKey, IdReportType type, int id) {
    this.datasetKey = datasetKey;
    this.type = type;
    this.id = id;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public IdReportType getType() {
    return type;
  }

  public void setType(IdReportType type) {
    this.type = type;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdReportEntry)) return false;
    IdReportEntry idReport = (IdReportEntry) o;
    return datasetKey == idReport.datasetKey && id == idReport.id && type == idReport.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, type, id);
  }
}

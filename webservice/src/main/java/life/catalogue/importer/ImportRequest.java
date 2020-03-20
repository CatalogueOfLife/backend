package life.catalogue.importer;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 */
public class ImportRequest implements Comparable<ImportRequest> {
  public final int datasetKey;
  public final boolean upload;
  public final boolean priority;
  public final boolean force;
  public Integer createdBy;
  public final LocalDateTime created = LocalDateTime.now();
  public LocalDateTime started;
  
  @JsonCreator
  public ImportRequest(@JsonProperty("datasetKey") int datasetKey,
                       @JsonProperty("force") boolean force,
                       @JsonProperty("priority") boolean priority
  ) {
   this(datasetKey, null, force, priority, false);
  }

  public ImportRequest(int datasetKey, int createdBy) {
    this(datasetKey, createdBy, false, false, false);
  }
  
  public ImportRequest(int datasetKey, Integer createdBy, boolean force, boolean priority, boolean upload) {
    this.datasetKey = datasetKey;
    this.upload = upload;
    this.createdBy = createdBy;
    this.force = force;
    this.priority = priority;
  }
  
  public void start() {
    started = LocalDateTime.now();
  }
  
  public int compareTo(ImportRequest other) {
    return Comparator.comparing((ImportRequest r) -> !r.priority)
        .thenComparing(r->r.created)
        .compare(this, other);
  }
 
  /**
   * Naturally equal if the datasetKey matches
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImportRequest that = (ImportRequest) o;
    return datasetKey == that.datasetKey;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey);
  }
  
  @Override
  public String toString() {
    return "ImportRequest{" +
        "datasetKey=" + datasetKey +
        ", priority=" + priority +
        ", force=" + force +
        '}';
  }
}

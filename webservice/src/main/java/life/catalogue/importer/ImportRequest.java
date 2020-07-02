package life.catalogue.importer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

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

  public static ImportRequest reimport(int datasetKey, int createdBy){
    return new ImportRequest(datasetKey, createdBy, true, false, true);
  }

  public static ImportRequest upload(int datasetKey, int createdBy){
    return new ImportRequest(datasetKey, createdBy, true, true, true);
  }

  public static ImportRequest external(int datasetKey, int createdBy){
    return new ImportRequest(datasetKey, createdBy, false, false, false);
  }

  /**
   * We exclude the upload flag from JSON so it cannot be triggered accidently from the API
   */
  @JsonCreator
  public ImportRequest(@JsonProperty("datasetKey") int datasetKey,
                       @JsonProperty("force") boolean force,
                       @JsonProperty("priority") boolean priority
  ) {
   this(datasetKey, null, force, priority, false);
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

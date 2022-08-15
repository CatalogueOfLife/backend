package life.catalogue.importer;

import java.nio.file.Path;
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
  public final Path upload;
  public final boolean priority;
  public final boolean force;
  public final Integer reimportAttempt;
  public Integer createdBy;
  public final LocalDateTime created = LocalDateTime.now();
  public LocalDateTime started;

  public static ImportRequest reimport(int datasetKey, int attempt, int createdBy){
    return new ImportRequest(datasetKey, createdBy, true, false, attempt, null);
  }

  public static ImportRequest upload(int datasetKey, int createdBy, Path upload){
    return new ImportRequest(datasetKey, createdBy, true, true, null, upload);
  }

  public static ImportRequest external(int datasetKey, int createdBy){
    return external(datasetKey, createdBy, false);
  }

  public static ImportRequest external(int datasetKey, int createdBy, boolean force){
    return external(datasetKey, createdBy, force, false);
  }

  public static ImportRequest external(int datasetKey, int createdBy, boolean force, boolean priority){
    return new ImportRequest(datasetKey, createdBy, force, priority, null, null);
  }

  /**
   * We exclude the upload & reimport flag from JSON so it cannot be triggered accidently from the API
   */
  @JsonCreator
  public ImportRequest(@JsonProperty("datasetKey") int datasetKey,
                       @JsonProperty("force") boolean force,
                       @JsonProperty("priority") boolean priority
  ) {
   this(datasetKey, null, force, priority, null, null);
  }

  private ImportRequest(int datasetKey, Integer createdBy, boolean force, boolean priority, Integer reimportAttempt, Path upload) {
    this.datasetKey = datasetKey;
    this.upload = upload;
    this.createdBy = createdBy;
    this.force = force;
    this.priority = priority;
    this.reimportAttempt = reimportAttempt;
  }

  public boolean hasUpload() {
    return upload != null;
  }

  public Path getUpload() {
    return upload;
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

package life.catalogue.importer;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

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
  /**
   * Optional callback URL that will receive a POST with the final DatasetImport JSON
   * once the job reaches a terminal state (success or failure).
   * See https://github.com/CatalogueOfLife/backend/issues/1552
   */
  @Nullable
  public URI callback;
  public final LocalDateTime created = LocalDateTime.now();
  public LocalDateTime started;

  public static ImportRequest reimport(int datasetKey, int attempt, int createdBy){
    return new ImportRequest(datasetKey, createdBy, true, false, attempt, null);
  }

  public static ImportRequest upload(int datasetKey, int createdBy, Path upload){
    return upload(datasetKey, createdBy, upload, null);
  }

  public static ImportRequest upload(int datasetKey, int createdBy, Path upload, @Nullable URI callback){
    ImportRequest req = new ImportRequest(datasetKey, createdBy, true, true, null, upload);
    req.callback = callback;
    return req;
  }

  /**
   * Rebuilds the request of an import job from the params it persisted in the job table, which is the request
   * itself serialized. Unlike the JSON creator this restores the upload and reimport flags, so it must only ever
   * read params the server wrote itself, never a request body.
   *
   * @param createdBy the user of the job, used when the params lack one
   * @return null if the params do not describe an import request
   */
  @Nullable
  public static ImportRequest fromParams(@Nullable JsonNode params, @Nullable Integer createdBy) {
    if (params == null || !params.hasNonNull("datasetKey")) {
      return null;
    }
    int datasetKey = params.get("datasetKey").asInt();
    Integer user = params.hasNonNull("createdBy") ? Integer.valueOf(params.get("createdBy").asInt()) : createdBy;
    Integer reimport = params.hasNonNull("reimportAttempt") ? params.get("reimportAttempt").asInt() : null;
    Path upload = params.hasNonNull("upload") ? toPath(params.get("upload").asText()) : null;
    var req = new ImportRequest(datasetKey, user,
      params.path("force").asBoolean(false), params.path("priority").asBoolean(false), reimport, upload);
    if (params.hasNonNull("callback")) {
      req.callback = URI.create(params.get("callback").asText());
    }
    return req;
  }

  /**
   * Jackson writes a Path as a file URI, but accept a plain path too.
   */
  private static Path toPath(String value) {
    return value.startsWith("file:") ? Paths.get(URI.create(value)) : Paths.get(value);
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
                       @JsonProperty("priority") boolean priority,
                       @JsonProperty("callback") @Nullable URI callback
  ) {
   this(datasetKey, null, force, priority, null, null);
   this.callback = callback;
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

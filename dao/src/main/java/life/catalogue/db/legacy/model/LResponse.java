package life.catalogue.db.legacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class LResponse {
  private final String version;
  private final String id;
  private final String name;
  private final int start;
  private final int total;
  private final List<LName> results;

  public LResponse(String id, String name, int start, String version) {
    this.version = version;
    this.id = id;
    this.name = name;
    this.start = start;
    this.total = 0;
    this.results = null;
  }

  public LResponse(String id, LName result, String version) {
    this.version = version;
    this.id = id;
    this.name = null;
    this.start = 0;
    this.total = 1;
    this.results = List.of(result);
  }

  public LResponse(String name, int total, int start, List<LName> results, String version) {
    this.version = version;
    this.id = null;
    this.name = name;
    this.start = start;
    this.total = total;
    this.results = results;
  }

  public String getVersion() {
    return version;
  }

  public String getId() {
    return id;
  }

  @JsonProperty("total_number_of_results")
  public int getTotal() {
    return total;
  }

  @JsonProperty("number_of_results_returned")
  public int getNumberOfResultsReturned() {
    return results == null ? 0 : results.size();
  }

  public List<?> getResults() {
    return results;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LResponse)) return false;
    LResponse lResponse = (LResponse) o;
    return start == lResponse.start &&
      total == lResponse.total &&
      Objects.equals(id, lResponse.id) &&
      Objects.equals(name, lResponse.name) &&
      Objects.equals(results, lResponse.results);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, start, total, results);
  }
}

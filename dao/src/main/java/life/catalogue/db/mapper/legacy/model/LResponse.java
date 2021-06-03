package life.catalogue.db.mapper.legacy.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName="results")
public class LResponse {
  private final String version;
  private final String id;
  private final String name;
  private final int start;
  private final int total;
  @JacksonXmlElementWrapper(useWrapping=false)
  private final List<LName> result;

  public LResponse(String id, String name, int start, String version) {
    this.version = nonNull(version);
    this.id = nonNull(id);
    this.name = nonNull(name);
    this.start = start;
    this.total = 0;
    this.result = null;
  }

  public LResponse(String id, LName result, String version) {
    this.version = nonNull(version);
    this.id = nonNull(id);
    this.name = nonNull(null);
    this.start = 0;
    this.total = 1;
    this.result = List.of(result);
  }

  public LResponse(String name, int total, int start, List<LName> results, String version) {
    this.version = nonNull(version);
    this.id = nonNull(null);
    this.name = nonNull(name);
    this.start = start;
    this.total = total;
    this.result = results;
  }

  static String nonNull(String x){
    return x == null ? "" : x;
  }

  @JacksonXmlProperty(isAttribute = true)
  public String getVersion() {
    return version;
  }

  @JacksonXmlProperty(isAttribute = true)
  public String getId() {
    return id;
  }

  @JsonProperty("total_number_of_results")
  @JacksonXmlProperty(isAttribute = true, localName = "total_number_of_results")
  public int getTotal() {
    return total;
  }

  @JsonProperty("number_of_results_returned")
  @JacksonXmlProperty(isAttribute = true, localName = "number_of_results_returned")
  public int getNumberOfResultsReturned() {
    return result == null ? 0 : result.size();
  }

  @JacksonXmlProperty(isAttribute = true)
  public String getName() {
    return name;
  }

  @JacksonXmlProperty(isAttribute = true)
  public int getStart() {
    return start;
  }

  public List<?> getResult() {
    return result;
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
      Objects.equals(result, lResponse.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, start, total, result);
  }
}

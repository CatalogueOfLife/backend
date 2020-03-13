package life.catalogue.api.search;

import java.util.List;
import java.util.Objects;
import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.vocab.Issue;

public class ReferenceSearchRequest {
  
  public static enum SortBy {
    NATIVE,
    YEAR,
    RELEVANCE
  }
  
  @QueryParam("q")
  private String q;
  
  @QueryParam("year")
  private Integer year;
  
  @QueryParam("sectorKey")
  private String sectorKey ;
  
  @QueryParam("issue")
  private List<Issue> issues;
  
  @QueryParam("sortBy")
  private SortBy sortBy;
  
  public static ReferenceSearchRequest byQuery(String query) {
    ReferenceSearchRequest q = new ReferenceSearchRequest();
    q.q = query;
    return q;
  }
  
  public boolean isEmpty() {
    return StringUtils.isBlank(q)
        && year == null
        && StringUtils.isBlank(sectorKey)
        && sortBy == null;
  }
  
  public String getQ() {
    return q;
  }
  
  public void setQ(String q) {
    this.q = q;
  }
  
  public Integer getYear() {
    return year;
  }
  
  public void setYear(Integer year) {
    this.year = year;
  }
  
  public String getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(String sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public List<Issue> getIssues() {
    return issues;
  }
  
  public void setIssues(List<Issue> issues) {
    this.issues = issues;
  }
  
  @JsonIgnore
  public Integer getSectorKeyInt() {
    try {
      return Integer.valueOf(sectorKey);
    } catch (NumberFormatException e) {
      return null;
    }
  }
  
  @JsonIgnore
  public boolean getSectorKeyIsNull() {
    return NameUsageSearchRequest.IS_NULL.equalsIgnoreCase(sectorKey);
  }
  
  public SortBy getSortBy() {
    return sortBy;
  }
  
  public void setSortBy(SortBy sortBy) {
    this.sortBy = Preconditions.checkNotNull(sortBy);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReferenceSearchRequest that = (ReferenceSearchRequest) o;
    return Objects.equals(q, that.q) &&
        Objects.equals(year, that.year) &&
        Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(issues, that.issues) &&
        sortBy == that.sortBy;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(q, year, sectorKey, issues, sortBy);
  }
}

package life.catalogue.api.search;

import java.time.LocalDate;
import java.util.Objects;
import javax.ws.rs.QueryParam;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetType;
import org.gbif.nameparser.api.NomCode;

public class DatasetSearchRequest {
  
  public static enum SortBy {
    KEY,
    TITLE,
    AUTHORS,
    RELEVANCE,
    CREATED,
    MODIFIED,
    SIZE
  }
  
  @QueryParam("q")
  private String q;
  
  @QueryParam("code")
  private NomCode code;
  
  @QueryParam("contributesTo")
  private Integer contributesTo;
  
  @QueryParam("format")
  private DataFormat format;
  
  @QueryParam("type")
  private DatasetType type;
  
  @QueryParam("modified")
  private LocalDate modified;
  
  @QueryParam("created")
  private LocalDate created;
  
  @QueryParam("released")
  private LocalDate released;
  
  @QueryParam("sortBy")
  private SortBy sortBy;
  
  @QueryParam("reverse")
  private boolean reverse = false;
  
  public static DatasetSearchRequest byQuery(String query) {
    DatasetSearchRequest q = new DatasetSearchRequest();
    q.q = query;
    return q;
  }
  
  public boolean isEmpty() {
    return StringUtils.isBlank(q) &&
        code == null &&
        contributesTo == null &&
        format == null &&
        type == null &&
        sortBy == null &&
        modified == null &&
        created == null &&
        released == null;
  }
  
  public String getQ() {
    return q;
  }
  
  public void setQ(String q) {
    this.q = q;
  }
  
  public NomCode getCode() {
    return code;
  }
  
  public void setCode(NomCode code) {
    this.code = code;
  }
  
  public Integer getContributesTo() {
    return contributesTo;
  }
  
  public void setContributesTo(Integer contributesTo) {
    this.contributesTo = contributesTo;
  }
  
  public DataFormat getFormat() {
    return format;
  }
  
  public void setFormat(DataFormat format) {
    this.format = format;
  }
  
  public DatasetType getType() {
    return type;
  }
  
  public void setType(DatasetType type) {
    this.type = type;
  }
  
  public LocalDate getModified() {
    return modified;
  }
  
  public void setModified(LocalDate modified) {
    this.modified = modified;
  }
  
  public LocalDate getReleased() {
    return released;
  }
  
  public void setReleased(LocalDate released) {
    this.released = released;
  }
  
  public LocalDate getCreated() {
    return created;
  }
  
  public void setCreated(LocalDate created) {
    this.created = created;
  }
  
  public SortBy getSortBy() {
    return sortBy;
  }
  
  public void setSortBy(SortBy sortBy) {
    this.sortBy = Preconditions.checkNotNull(sortBy);
  }
  
  public boolean isReverse() {
    return reverse;
  }
  
  public void setReverse(boolean reverse) {
    this.reverse = reverse;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DatasetSearchRequest that = (DatasetSearchRequest) o;
    return reverse == that.reverse &&
        Objects.equals(q, that.q) &&
        code == that.code &&
        Objects.equals(contributesTo, that.contributesTo) &&
        format == that.format &&
        type == that.type &&
        Objects.equals(modified, that.modified) &&
        Objects.equals(created, that.created) &&
        Objects.equals(released, that.released) &&
        sortBy == that.sortBy;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(q, code, contributesTo, format, type, modified, created, released, sortBy, reverse);
  }
}

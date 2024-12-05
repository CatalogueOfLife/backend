
package life.catalogue.api.search;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.QueryParam;

public class NameUsageSearchRequest extends NameUsageRequest {

  public enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP
  }

  static final Set<SearchContent> DEFAULT_CONTENT = Sets.immutableEnumSet(SearchContent.SCIENTIFIC_NAME, SearchContent.AUTHORSHIP);

  public enum SortBy {
    NAME, TAXONOMIC, INDEX_NAME_ID, NATIVE, RELEVANCE
  }

  @QueryParam("facet")
  private Set<NameUsageSearchParameter> facets = EnumSet.noneOf(NameUsageSearchParameter.class);

  @QueryParam("facetLimit")
  @Min(0)
  private Integer facetLimit;

  @QueryParam("content")
  private Set<SearchContent> content = EnumSet.copyOf(DEFAULT_CONTENT);

  @QueryParam("highlight")
  private boolean highlight;

  @QueryParam("type")
  private SearchType searchType;

  public NameUsageSearchRequest() {}

  public NameUsageSearchRequest(NameUsageSearchRequest.SearchContent content) {
    setSingleContent(content);
  }

  @JsonCreator
  public NameUsageSearchRequest(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters,
      @JsonProperty("facet") Set<NameUsageSearchParameter> facets,
      @JsonProperty("facetLimit") @Min(0) Integer facetLimit,
      @JsonProperty("content") Set<SearchContent> content,
      @JsonProperty("sortBy") SortBy sortBy,
      @JsonProperty("q") String q,
      @JsonProperty("highlight") boolean highlight,
      @JsonProperty("reverse") boolean reverse,
      @JsonProperty("fuzzy") boolean fuzzy,
      @JsonProperty("type") SearchType searchType,
      @JsonProperty("minRank") Rank minRank,
      @JsonProperty("maxRank") Rank maxRank) {
    super(q, fuzzy, minRank, maxRank, sortBy, reverse);
    this.highlight = highlight;
    this.searchType = searchType;
    this.facetLimit = facetLimit;
    setFilters(filters);
    setFacets(facets);
    setContent(content);
  }

  /**
   * Creates a nearly deep copy of this NameSearchRequest, but does deep copy the filters map values!
   * The filters map is copied using EnumMap's copy constructor.
   * Therefore you should not manipulate the filter values (which are lists) as they are
   * copied by reference. You can, however, simply replace the list with another list, and you can
   * also add/remove facets and search content without affecting the original request.
   */
  public NameUsageSearchRequest(NameUsageSearchRequest other) {
    super(other);
    this.highlight = other.highlight;
    this.searchType = other.searchType;
    this.facetLimit = other.facetLimit;
    setFacets(other.facets);
    setContent(other.content);
  }



  /**
   * Creates a shallow copy of this NameSearchRequest. The filters map is copied using EnumMap's copy
   * constructor. Therefore you should not manipulate the filter values (which are lists) as they are
   * copied by reference. You can, however, simply replace the list with another list, and you can
   * also add/remove facets and search content without affecting the original request.
   */
  public NameUsageSearchRequest copy() {
    return new NameUsageSearchRequest(this);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty() &&
        (content == null || content.isEmpty())
        && (facets == null || facets.isEmpty())
        && (getFilters() == null || getFilters().isEmpty())
        && !highlight
        && !fuzzy
        && searchType == null;
  }

  public void setFacets(Set<NameUsageSearchParameter> facets) {
    this.facets = facets == null || facets.isEmpty() ? EnumSet.noneOf(NameUsageSearchParameter.class) : EnumSet.copyOf(facets);
  }

  public void addFacet(NameUsageSearchParameter facet) {
    getFacets().add(facet);
  }

  public Set<NameUsageSearchParameter> getFacets() {
    return facets;
  }

  public Integer getFacetLimit() {
    return facetLimit;
  }

  public void setFacetLimit(Integer facetLimit) {
    this.facetLimit = facetLimit;
  }

  public Set<SearchContent> getContent() {
    return content;
  }

  public void setSingleContent(SearchContent content) {
    this.content = content == null ? EnumSet.copyOf(DEFAULT_CONTENT) : EnumSet.of(content);
  }

  public void setContent(Set<SearchContent> content) {
    if (content == null || content.isEmpty()) {
      setContentDefault();
    } else {
      this.content = EnumSet.copyOf(content);
    }
  }

  public void setContentDefault() {
    this.content = EnumSet.copyOf(DEFAULT_CONTENT);
  }

  public boolean isHighlight() {
    return highlight;
  }

  public void setHighlight(boolean highlight) {
    this.highlight = highlight;
  }

  @Override
  public SearchType getSearchType() {
    return searchType;
  }

  public void setSearchType(SearchType searchType) {
    this.searchType = searchType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameUsageSearchRequest)) return false;
    if (!super.equals(o)) return false;
    NameUsageSearchRequest that = (NameUsageSearchRequest) o;
    return highlight == that.highlight
           && Objects.equals(facets, that.facets)
           && Objects.equals(facetLimit, that.facetLimit)
           && Objects.equals(content, that.content)
           && searchType == that.searchType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), facets, facetLimit, content, highlight, searchType);
  }
}

package life.catalogue.resources;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;

@Produces(MediaType.APPLICATION_JSON)
@Path("/nameusage")
public class NameUsageSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchResource.class);

  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageSearchResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameUsageSearchRequest query,
      @Valid @BeanParam Page page,
      @Context UriInfo uri) throws InvalidQueryException {
    if (uri != null) {
      query.addFilters(uri.getQueryParameters());
    }
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@Valid SearchRequestBody req, @Context UriInfo uri) throws InvalidQueryException {
    return search(req.request, req.page, uri);
  }

  public static class SearchRequestBody {

    @Valid
    public final NameUsageSearchRequest request;
    @Valid
    public final Page page;

    @JsonCreator
    public SearchRequestBody(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) List<Object>> filter,
        @JsonProperty("facet") Set<NameUsageSearchParameter> facet,
        @JsonProperty("content") Set<NameUsageSearchRequest.SearchContent> content,
        @JsonProperty("sortBy") NameUsageSearchRequest.SortBy sortBy,
        @JsonProperty("highlight") @DefaultValue("false") boolean highlight,
        @JsonProperty("reverse") @DefaultValue("false") boolean reverse,
        @JsonProperty("fuzzy") @DefaultValue("false") boolean fuzzy,
        @JsonProperty("prefix") @DefaultValue("false") boolean prefix,
        @JsonProperty("offset") @DefaultValue("0") int offset,
        @JsonProperty("limit") @DefaultValue("10") int limit) {
      request = new NameUsageSearchRequest(filter, facet, content, sortBy, highlight, reverse, fuzzy, prefix);
      page = new Page(offset, limit);
    }
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggest(@BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }
}

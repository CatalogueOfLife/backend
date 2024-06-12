package life.catalogue.resources;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Produces(MediaType.APPLICATION_JSON)
@Path("/nameusage")
public class NameUsageSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchResource.class);

  private final NameUsageSearchService searchService;

  public NameUsageSearchResource(NameUsageSearchService search) {
    this.searchService = search;
  }


  @GET
  public ResultPage<NameUsageBase> list(@QueryParam("nidx") Integer namesIndexID,
                                        @QueryParam("id") String id,
                                        @Valid @BeanParam Page page,
                                        @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    if (namesIndexID == null && id == null) {
      throw new IllegalArgumentException("nidx or id parameter required");
    }
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    List<NameUsageBase> result = id != null ?
      num.listByUsageID(id, page) :
      num.listByNamesIndexIDGlobal(namesIndexID, page);
    return new ResultPage<>(p, result, () -> id != null ?
      num.countByUsageID(id) :
      num.countByNamesIndexID(namesIndexID, null)
    );
  }

  @GET
  @Path("search")
  public NameUsageSearchResponse search(@BeanParam NameUsageSearchRequest query,
                                        @Valid @BeanParam Page page,
                                        @Context ContainerRequestContext ctx,
                                        @Context UriInfo uri) throws InvalidQueryException {
    if (uri != null) {
      query.addFilters(uri.getQueryParameters());
    }
    if (query.hasFilter(NameUsageSearchParameter.CATALOGUE_KEY)) {
      ResourceUtils.dontCache(ctx);
    }
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public NameUsageSearchResponse searchPOST(@Valid SearchRequestBody req,
                                            @Context ContainerRequestContext ctx,
                                            @Context UriInfo uri) throws InvalidQueryException {
    return search(req.request, req.page, ctx, uri);
  }

  public static class SearchRequestBody {

    @Valid
    public final NameUsageSearchRequest request;
    @Valid
    public final Page page;

    @JsonCreator
    public SearchRequestBody(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filter,
        @JsonProperty("facet") Set<NameUsageSearchParameter> facet,
        @JsonProperty("facetLimit") @Min(0) Integer facetLimit,
        @JsonProperty("content") Set<NameUsageSearchRequest.SearchContent> content,
        @JsonProperty("sortBy") NameUsageSearchRequest.SortBy sortBy,
        @JsonProperty("q") String q,
        @JsonProperty("highlight") @DefaultValue("false") boolean highlight,
        @JsonProperty("reverse") @DefaultValue("false") boolean reverse,
        @JsonProperty("fuzzy") @DefaultValue("false") boolean fuzzy,
        @JsonProperty("offset") @DefaultValue("0") int offset,
        @JsonProperty("limit") @DefaultValue("10") int limit,
        @JsonProperty("type") NameUsageRequest.SearchType searchType,
        @JsonProperty("minRank") Rank minRank,
        @JsonProperty("maxRank") Rank maxRank) {
      request = new NameUsageSearchRequest(filter, facet, facetLimit, content, sortBy, q, highlight, reverse, fuzzy, searchType, minRank, maxRank);
      page = new Page(offset, limit);
    }
  }

}

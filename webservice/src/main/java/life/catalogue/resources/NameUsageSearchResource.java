package life.catalogue.resources;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
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
                                        @Valid @BeanParam Page page,
                                        @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    if (namesIndexID == null) {
      throw new IllegalArgumentException("nidx parameter required");
    }
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
    List<NameUsageBase> result = num.listByNamesIndexIDGlobal(namesIndexID, page);
    return new ResultPage<>(p, result, () -> nmm.count(namesIndexID, null));
  }

  @GET
  @Path("search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameUsageSearchRequest query, @Valid @BeanParam Page page, @Context UriInfo uri) throws InvalidQueryException {
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
    public SearchRequestBody(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filter,
        @JsonProperty("facet") Set<NameUsageSearchParameter> facet,
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
      request = new NameUsageSearchRequest(filter, facet, content, sortBy, q, highlight, reverse, fuzzy, searchType, minRank, maxRank);
      page = new Page(offset, limit);
    }
  }

}

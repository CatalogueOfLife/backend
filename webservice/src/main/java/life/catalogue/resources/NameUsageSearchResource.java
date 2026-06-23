package life.catalogue.resources;

import io.swagger.v3.oas.annotations.Hidden;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.db.mapper.*;
import life.catalogue.es.query.InvalidQueryException;
import life.catalogue.es.search.NameUsageSearchService;

import life.catalogue.img.ThumborService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

@Produces(MediaType.APPLICATION_JSON)
@Path("/nameusage")
public class NameUsageSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchResource.class);

  private final NameUsageSearchService searchService;
  private final SqlSessionFactory factory;
  private final ThumborService thumborService;

  public NameUsageSearchResource(SqlSessionFactory factory, NameUsageSearchService search, ThumborService thumborService) {
    this.factory = factory;
    this.searchService = search;
    this.thumborService = thumborService;
  }


  @GET
  public ResultPage<NameUsageBase> list(@QueryParam("nidx") Integer namesIndexID,
                                        @QueryParam("id") String id,
                                        @Valid @BeanParam Page page) {
    try (SqlSession session = factory.openSession(true)) {
      Page p = page == null ? new Page() : page;
      if (namesIndexID == null && id == null) {
        throw new IllegalArgumentException("nidx or id parameter required");
      }
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      List<NameUsageBase> result = id != null ?
        num.listByUsageID(id, p) :
        num.listByNamesIndexIDGlobal(namesIndexID, p);
      return new ResultPage<>(p, result, () -> id != null ?
        num.countByUsageID(id) :
        num.countByNamesIndexID(namesIndexID, null)
      );
    }
  }

  private <T extends ExtensionEntity> List<T> listByNidx(Class<? extends TaxonExtensionMapper<T>> mapperCls, Integer nidx, Page page) {
    if (nidx == null) {
      throw new IllegalArgumentException("nidx parameter required");
    }
    try (SqlSession session = factory.openSession(true)) {
      var mapper = session.getMapper(mapperCls);
      return mapper.listByNamesIndexIDGlobal(nidx, page == null ? new Page() : page);
    }
  }

  @GET
  @Hidden
  @Path("property")
  public List<TaxonProperty> listProperties(@QueryParam("nidx") Integer namesIndexID, @Valid @BeanParam Page page) {
    return listByNidx(TaxonPropertyMapper.class, namesIndexID, page);
  }

  @GET
  @Hidden
  @Path("distribution")
  public List<Distribution> listDistribution(@QueryParam("nidx") Integer namesIndexID, @Valid @BeanParam Page page) {
    return listByNidx(DistributionMapper.class, namesIndexID, page);
  }

  @GET
  @Hidden
  @Path("media")
  public List<Media> listMedia(@QueryParam("nidx") Integer namesIndexID, @Valid @BeanParam Page page) {
    var media = listByNidx(MediaMapper.class, namesIndexID, page);
    media.forEach(thumborService::addThumbnail);
    return media;
  }

  @GET
  @Hidden
  @Path("vernacular")
  public List<VernacularName> listVernacular(@QueryParam("nidx") Integer namesIndexID, @Valid @BeanParam Page page) {
    return listByNidx(VernacularNameMapper.class, namesIndexID, page);
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
    if (query.hasFilter(NameUsageSearchParameter.PROJECT_KEY)) {
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
        @JsonProperty("facetOffset") @Min(0) Integer facetOffset,
        @JsonProperty("facetMinCount") @Min(0) Integer facetMinCount,
        @JsonProperty("facetIncludeSelf") Boolean facetIncludeSelf,
        @JsonProperty("content") Set<NameUsageSearchRequest.SearchContent> content,
        @JsonProperty("sortBy") NameUsageSearchRequest.SortBy sortBy,
        @JsonProperty("q") String q,
        @JsonProperty("reverse") @DefaultValue("false") boolean reverse,
        @JsonProperty("offset") @DefaultValue("0") int offset,
        @JsonProperty("limit") @DefaultValue("10") int limit,
        @JsonProperty("type") NameUsageRequest.SearchType searchType,
        @JsonProperty("minRank") Rank minRank,
        @JsonProperty("maxRank") Rank maxRank) {
      request = new NameUsageSearchRequest(filter, facet, facetLimit, facetOffset, facetMinCount, facetIncludeSelf, content, sortBy, q, reverse, searchType, minRank, maxRank);
      page = new Page(offset, limit);
    }
  }

}

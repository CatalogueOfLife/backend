package life.catalogue.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.*;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{key}/nameusage")
public class NameUsageResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResource.class);
  private static final Joiner COMMA_CAT = Joiner.on(';').skipNulls();
  private static final Object[][] EXPORT_HEADERS = new Object[1][];
  private static final Object[][] EXPORT_HEADERS_ISSUES = new Object[1][];
  static {
    EXPORT_HEADERS[0] = new Object[]{"ID", "parentID", "status", "rank", "scientificName", "authorship"};
    EXPORT_HEADERS_ISSUES[0] = Arrays.copyOf(EXPORT_HEADERS[0], 7);
    EXPORT_HEADERS_ISSUES[0][6] = "issues";
  }
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  public ResultPage<NameUsageBase> list(@PathParam("key") int datasetKey,
                                        @QueryParam("q") String q,
                                        @QueryParam("rank") Rank rank,
                                        @QueryParam("nidx") Integer namesIndexID,
                                        @Valid Page page,
                                        @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
    List<NameUsageBase> result;
    Supplier<Integer> count;
    if (namesIndexID != null) {
      result = mapper.listByNamesIndexID(datasetKey, namesIndexID, p);
      NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
      count = () -> nmm.count(namesIndexID, datasetKey);
    } else if (q != null) {
      result = mapper.listByName(datasetKey, q, rank, p);
      count = () -> result.size();
    } else {
      result = mapper.list(datasetKey, p);
      count = () -> mapper.count(datasetKey);
    }
    return new ResultPage<>(p, result, count);
  }

  @GET
  @RolesAllowed({Roles.ADMIN})
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("key") int datasetKey,
                                    @QueryParam("issue") boolean withIssues,
                                    @QueryParam("min") Rank min,
                                    @QueryParam("max") Rank max,
                                    @Context SqlSession session) {
    if (withIssues) {
      NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
      return Stream.concat(
        Stream.of(EXPORT_HEADERS_ISSUES),
        Streams.stream(nuwm.processDatasetUsageWithIssues(datasetKey)).map(this::map)
      );
    } else {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      return Stream.concat(
        Stream.of(EXPORT_HEADERS),
        Streams.stream(num.processDataset(datasetKey, min, max)).map(this::map)
      );
    }
  }

  private Object[] map(NameUsageBase nu){
    return new Object[]{
      nu.getId(),
      nu.getParentId(),
      nu.getStatus(),
      nu.getName().getRank(),
      nu.getName().getScientificName(),
      nu.getName().getAuthorship()
    };
  }

  private Object[] map(NameUsageWrapper nuw){
    NameUsageBase nu = (NameUsageBase) nuw.getUsage();
    return new Object[]{
      nu.getId(),
      nu.getParentId(),
      nu.getStatus(),
      nu.getName().getRank(),
      nu.getName().getScientificName(),
      nu.getName().getAuthorship(),
      COMMA_CAT.join(nuw.getIssues())
    };
  }

  @GET
  @Path("{id}")
  public NameUsageWrapper getByID(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    req.addFilter(NameUsageSearchParameter.USAGE_ID, id);
    ResultPage<NameUsageWrapper> results = searchService.search(req, new Page());
    if (results.size()==1) {
      return results.getResult().get(0);
    }
    throw NotFoundException.notFound(NameUsage.class, datasetKey, id);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> searchDataset(@PathParam("key") int datasetKey,
                                                    @BeanParam NameUsageSearchRequest query,
                                                    @Valid @BeanParam Page page,
                                                    @Context UriInfo uri) throws InvalidQueryException {
    checkIllegalDatasetKeyParam(datasetKey, query, uri);
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@PathParam("key") int datasetKey,
                                                 @Valid NameUsageSearchResource.SearchRequestBody req,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    return searchDataset(datasetKey, req.request, req.page, uri);
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("key") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    checkIllegalDatasetKeyParam(datasetKey, query, uri);
    return suggestService.suggest(query);
  }

  NameUsageRequest checkIllegalDatasetKeyParam(int datasetKey, NameUsageRequest query, UriInfo uri){
    query.addFilters(uri.getQueryParameters());
    if (query.hasFilter(NameUsageSearchParameter.DATASET_KEY) && !query.getFilterValue(NameUsageSearchParameter.DATASET_KEY).equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetFilter(datasetKey);
    return query;
  }
}

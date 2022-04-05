package life.catalogue.resources;

import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.util.RegexUtils;
import life.catalogue.db.mapper.ArchivedNameMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{key}/nameusage")
public class NameUsageResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResource.class);
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
                                        @Valid @BeanParam Page page,
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
  @Path("{id}")
  public NameUsageBase get(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    var key = DSID.of(datasetKey, id);
    NameUsageBase u = session.getMapper(NameUsageMapper.class).get(key);
    if (u == null) {
      // try name usage archive
      u = session.getMapper(ArchivedNameMapper.class).get(key);
    }
    return u;
  }

  @GET
  @Path("{id}/related")
  public List<NameUsageBase> related(@PathParam("key") int datasetKey,
                                     @PathParam("id") String id,
                                     @QueryParam("datasetKey") List<Integer> datasetKeys,
                                     @QueryParam("publisherKey") UUID publisherKey,
                                     @Context SqlSession session) {
    if ((datasetKeys == null || datasetKeys.isEmpty()) && publisherKey == null) {
      throw new IllegalArgumentException("datasetKey or publisherKey parameter is required");
    }
    return session.getMapper(NameUsageMapper.class).listRelated(DSID.of(datasetKey, id), datasetKeys, publisherKey);
  }

  @GET
  @Path("{id}/source")
  public VerbatimSource source(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VerbatimSourceMapper.class).get(DSID.of(datasetKey, id));
  }

  @GET
  @Timed
  @Path("pattern")
  public List<SimpleName> searchDatasetByRegex(@PathParam("key") int datasetKey,
                                                  @QueryParam("regex") String regex,
                                                  @QueryParam("status") TaxonomicStatus status,
                                                  @QueryParam("rank") Rank rank,
                                                  @Valid @BeanParam Page page,
                                                  @Context SqlSession session) {
    RegexUtils.validatePattern(regex);
    Page p = page == null ? new Page() : page;
    return session.getMapper(NameUsageMapper.class).listByRegex(datasetKey, regex, status, rank, p);
  }

  @GET
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

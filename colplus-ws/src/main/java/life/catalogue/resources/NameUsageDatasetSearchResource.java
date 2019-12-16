package life.catalogue.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.*;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.name.search.NameUsageSearchService;
import life.catalogue.es.name.suggest.NameUsageSuggestionService;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.stream.Stream;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{datasetKey}/nameusage")
public class NameUsageDatasetSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDatasetSearchResource.class);
  private static final Joiner COMMA_CAT = Joiner.on(',').skipNulls();
  private static final Object[][] NAME_HEADER = new Object[1][];
  static {
    NAME_HEADER[0] = new Object[]{"ID", "parentID", "status", "rank", "scientificName", "authorship", "issues"};
  }
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageDatasetSearchResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("datasetKey") int datasetKey,
                                  @QueryParam("issue") boolean withIssueOnly,
                                  @Context SqlSession session) {
    NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
    return Stream.concat(
            Stream.of(NAME_HEADER),
            Streams.stream(nuwm.processDatasetUsageOnly(datasetKey, withIssueOnly))
              .map(nu -> new Object[]{
                  nu.getId(),
                  ((NameUsageBase) nu.getUsage()).getParentId(),
                  nu.getUsage().getStatus(),
                  nu.getUsage().getName().getRank(),
                  nu.getUsage().getName().getScientificName(),
                  nu.getUsage().getName().getAuthorship(),
                  COMMA_CAT.join(nu.getIssues())
            })
    );
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> searchDataset(@PathParam("datasetKey") int datasetKey,
                                                    @BeanParam NameUsageSearchRequest query,
                                                    @Valid @BeanParam Page page,
                                                    @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    if (query.hasFilter(NameUsageSearchParameter.DATASET_KEY)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, search already scoped to datasetKey=" + datasetKey);
    }
    query.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    return searchService.search(query, page);
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("datasetKey") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    if (query.getDatasetKey() != null && !query.getDatasetKey().equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetKey(datasetKey);
    return suggestService.suggest(query);
  }
}

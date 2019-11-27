package life.catalogue.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.*;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.name.search.NameUsageSearchService;
import life.catalogue.es.name.suggest.NameUsageSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{datasetKey}/nameusage")
public class NameUsageDatasetSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDatasetSearchResource.class);

  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageDatasetSearchResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
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

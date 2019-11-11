package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.*;
import org.col.es.InvalidQueryException;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.name.suggest.NameUsageSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
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
  @Path("/nameusage/search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameUsageSearchRequest query,
      @Valid @BeanParam Page page,
      @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    return searchService.search(query, page);
  }
  
  @GET
  @Timed
  @Path("/nameusage/suggest")
  public NameUsageSuggestResponse suggest(@BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }
  
  @GET
  @Timed
  @Path("/dataset/{datasetKey}/nameusage/search")
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
  @Path("/dataset/{datasetKey}/nameusage/suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("datasetKey") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    if (query.getDatasetKey() != null && !query.getDatasetKey().equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetKey(datasetKey);
    return suggestService.suggest(query);
  }
}

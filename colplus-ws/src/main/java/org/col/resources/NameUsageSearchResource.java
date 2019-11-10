package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameUsageSearchRequest;
import org.col.api.search.NameUsageSuggestRequest;
import org.col.api.search.NameUsageSuggestResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.InvalidQueryException;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.name.suggest.NameUsageSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/nameusage")
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
  @Path("/search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameUsageSearchRequest query,
      @Valid @BeanParam Page page,
      @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    return searchService.search(query, page);
  }

  @GET
  @Timed
  @Path("/suggest")
  public NameUsageSuggestResponse suggest(@BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }

}

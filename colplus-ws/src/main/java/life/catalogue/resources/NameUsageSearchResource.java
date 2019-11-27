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
    query.addFilters(uri.getQueryParameters());
    return searchService.search(query, page);
  }
  
  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggest(@BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }
}

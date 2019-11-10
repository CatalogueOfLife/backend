package org.col.resources;

import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.*;
import org.col.dao.NameDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.es.InvalidQueryException;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.name.suggest.NameUsageSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource extends AbstractDatasetScopedResource<Name> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  private final NameDao dao;
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameResource(NameUsageSearchService svc, NameDao dao, NameUsageSuggestionService suggestService) {
    super(Name.class, dao);
    this.dao = dao;
    this.searchService = svc;
    this.suggestService = suggestService;
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> search(@PathParam("datasetKey") int datasetKey, @BeanParam NameUsageSearchRequest query,
                                             @Valid @BeanParam Page page, @Context UriInfo uri) throws InvalidQueryException {
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
  public NameUsageSuggestResponse suggest(@PathParam("datasetKey") int datasetKey, @BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    if (query.getDatasetKey() != null && !query.getDatasetKey().equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetKey(datasetKey);
    return suggestService.suggest(query);
  }
  
  @GET
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return dao.homotypicGroup(datasetKey, id);
  }

  @GET
  @Path("{id}/relations")
  public List<NameRelation> getRelations(@PathParam("datasetKey") int datasetKey,
      @PathParam("id") String id, @Context SqlSession session) {
    NameRelationMapper mapper = session.getMapper(NameRelationMapper.class);
    return mapper.list(datasetKey, id);
  }

  @GET
  @Path("{id}/group")
  public List<Name> getIndexGroup(@PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(NameMapper.class).indexGroup(id);
  }
}

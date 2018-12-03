package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.*;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.col.db.dao.NameDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.es.InvalidQueryException;
import org.col.es.NameUsageSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);
  
  private final NameUsageSearchService searchService;
  
  public NameResource(NameUsageSearchService svc) {
    this.searchService = svc;
  }
  
  @GET
  public ResultPage<Name> list(@PathParam("datasetKey") Integer datasetKey,
                               @Valid @BeanParam Page page, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.list(datasetKey, page);
  }
  
  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper<NameUsage>> search(@PathParam("datasetKey") int datasetKey,
                                                        @BeanParam NameSearchRequest query,
                                                        @Valid @BeanParam Page page,
                                                        @Context UriInfo uri) throws InvalidQueryException {
    query.addQueryParams(uri.getQueryParameters());
    if (query.hasFilter(NameSearchParameter.DATASET_KEY)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, search already scoped to datasetKey=" + datasetKey);
    }
    query.addFilter(NameSearchParameter.DATASET_KEY, datasetKey);
    return searchService.search(query, page);
  }
  
  @GET
  @Path("{id}")
  public Name get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    Name name = dao.get(datasetKey, id);
    if (name == null) {
      throw NotFoundException.idNotFound(Name.class, datasetKey, id);
    }
    return name;
  }
  
  @GET
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
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

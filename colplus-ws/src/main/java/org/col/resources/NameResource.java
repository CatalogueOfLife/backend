package org.col.resources;

import java.util.List;
import java.util.Map;

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
import org.col.api.exception.NotFoundException;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.col.api.util.VocabularyUtils;
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
  public ResultPage<NameUsageWrapper<?>> search(@BeanParam NameSearchRequest query,
      @Valid @BeanParam Page page, @Context UriInfo uri) {
    addQueryParams(query, uri);
    try {
      return searchService.search(query, page);
    } catch (InvalidQueryException e) {
      // There's no real chance that a NameSearchRequest will translate into an invalid query
      throw new AssertionError(e.getMessage());
    }
  }

  private static void addQueryParams(NameSearchRequest req, UriInfo uri) {
    for (Map.Entry<String, List<String>> qp : uri.getQueryParameters().entrySet()) {
      VocabularyUtils.lookup(qp.getKey(), NameSearchParameter.class).ifPresent(p -> {
        req.addAll(p, qp.getValue());
      });
    }
  }

  @GET
  @Path("{id}")
  public Name get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id,
      @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    Name name = dao.get(datasetKey, id);
    if (name == null) {
      throw NotFoundException.idNotFound(Name.class, datasetKey, id);
    }
    return name;
  }

  @GET
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id,
      @Context SqlSession session) {
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

  /**
   * TODO: this is really a names index / prov catalogue specific method. Move it to a dedicated web
   * resource
   */
  @GET
  @Path("{id}/group")
  public List<Name> getIndexGroup(@PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(NameMapper.class).indexGroup(id);
  }
}

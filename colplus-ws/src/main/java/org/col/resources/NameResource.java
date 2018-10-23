package org.col.resources;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.util.VocabularyUtils;
import org.col.db.dao.NameDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
  public ResultPage<Name> list(@PathParam("datasetKey") Integer datasetKey,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.list(datasetKey, page);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsage> search(@BeanParam NameSearchRequest query, @Valid @BeanParam Page page,
                                      @Context UriInfo uri, @Context SqlSession session) {
    addQueryParams(query, uri);
    
    //TODO: execute ES search
    throw new NotSupportedException("Awaiting Elastic Search");
  }
  
  private void addQueryParams(NameSearchRequest req, UriInfo uri) {
    for (Map.Entry<String, List<String>> qp : uri.getQueryParameters().entrySet()) {
      VocabularyUtils.lookup(qp.getKey(), NameSearchParameter.class).ifPresent(p -> {
        req.addAll(p, qp.getValue());
      });
    }
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
  public List<NameRelation> getRelations(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
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

package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.dao.NameDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource extends AbstractDatasetScopedResource<Name> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  private final NameDao dao;

  public NameResource(NameDao dao) {
    super(Name.class, dao);
    this.dao = dao;
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

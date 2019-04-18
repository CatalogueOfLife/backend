package org.col.resources;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Reference;
import org.col.dao.DatasetEntityDao;
import org.col.db.mapper.ReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource extends DatasetEntityResource<Reference>  {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);
  
  public ReferenceResource(DatasetEntityDao<Reference, ReferenceMapper> dao) {
    super(Reference.class, dao);
  }
  
  @GET
  @Path("{id}")
  public Reference get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @QueryParam("page") String page,
                       @Context SqlSession session) {
    Reference ref = super.get(datasetKey, id);
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }
  
}

package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.db.dao.ReferenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);

  @GET
  public ResultPage<Reference> list(@QueryParam("datasetKey") Integer datasetKey,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.list(datasetKey, page);
  }

  @GET
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.lookupKey(id, datasetKey);
  }

  @GET
  @Path("{key}")
  public Reference get(@PathParam("key") int key, @QueryParam("page") String page, @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.get(key, page);
  }

}

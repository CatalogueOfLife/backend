package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.db.dao.ReferenceDao;
import org.col.dw.jersey.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);

  @GET
  public ResultPage<Reference> list(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page, @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.list(datasetKey, page);
  }

  @GET
  @Path("{id}")
  public Reference get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @QueryParam("page") String page,
      @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    Reference ref = dao.get(datasetKey, id, page);
    if (ref == null) {
      throw NotFoundException.idNotFound(Reference.class, datasetKey, id);
    }
    return ref;
  }

}

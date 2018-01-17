package org.col.resources;

import java.util.List;
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
import org.col.api.NameAct;
import org.col.api.Page;
import org.col.api.Reference;
import org.col.api.ResultPage;
import org.col.dao.ReferenceDao;
import org.col.db.KeyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.annotation.Timed;

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
  @Timed
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.lookupKey(id, datasetKey);
  }

  @GET
  @Timed
  @Path("{key}")
  public Reference get(@PathParam("key") int key, @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    Reference r = dao.get(key);
    if (r == null) {
      throw new KeyNotFoundException(Reference.class, key);
    }
    return r;
  }

  @GET
  @Timed
  @Path("{key}/acts")
  public List<NameAct> getNameActs(@PathParam("key") int referenceKey,
      @Context SqlSession session) {
    ReferenceDao dao = new ReferenceDao(session);
    return dao.getNameActs(referenceKey);
  }

}

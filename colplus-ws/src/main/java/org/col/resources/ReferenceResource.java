package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.api.search.ReferenceSearchRequest;
import org.col.dao.ReferenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource extends DatasetEntityResource<Reference>  {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);
  private final ReferenceDao dao;
  
  public ReferenceResource(ReferenceDao dao) {
    super(Reference.class, dao);
    this.dao = dao;
  }
  
  @GET
  @Path("search")
  public ResultPage<Reference> search(@PathParam("datasetKey") int datasetKey,
                                      @BeanParam ReferenceSearchRequest req,
                                      @Valid @BeanParam Page page,
                                      @Context SqlSession session) {
    return dao.search(datasetKey, req, page);
  }
}

package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
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
  public ResultPage<Reference> list(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page) {
    return dao.list(datasetKey, page);
  }
}

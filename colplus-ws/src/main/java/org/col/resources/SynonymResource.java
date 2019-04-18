package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.Synonym;
import org.col.dao.SynonymDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/synonym")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SynonymResource extends DatasetEntityResource<Synonym> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SynonymResource.class);
  private final SynonymDao dao;
  
  public SynonymResource(SynonymDao dao) {
    super(Synonym.class, dao);
    this.dao = dao;
  }
  
  @GET
  public ResultPage<Synonym> list(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page) {
    return dao.list(datasetKey, page);
  }
  
}

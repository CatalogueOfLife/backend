package org.col.resources;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.col.api.model.Synonym;
import org.col.dao.SynonymDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/synonym")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SynonymResource extends AbstractDatasetScopedResource<Synonym> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SynonymResource.class);
  private final SynonymDao dao;
  
  public SynonymResource(SynonymDao dao) {
    super(Synonym.class, dao);
    this.dao = dao;
  }
  
}

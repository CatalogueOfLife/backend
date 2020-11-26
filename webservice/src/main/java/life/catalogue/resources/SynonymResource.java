package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.dao.SynonymDao;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{key}/synonym")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SynonymResource extends AbstractDatasetScopedResource<String, Synonym, Page> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SynonymResource.class);
  private final SynonymDao dao;
  
  public SynonymResource(SynonymDao dao) {
    super(Synonym.class, dao);
    this.dao = dao;
  }

  @GET
  @Path("{id}/source")
  public VerbatimSource source(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VerbatimSourceMapper.class).get(DSID.of(datasetKey, id));
  }

}

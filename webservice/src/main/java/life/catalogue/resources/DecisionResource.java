package life.catalogue.resources;

import com.google.common.base.Preconditions;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.ColUser;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends AbstractDecisionResource<EditorialDecision> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  private final DecisionDao dao;
  
  public DecisionResource(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(EditorialDecision.class, new DecisionDao(factory, indexService), factory);
    this.dao = (DecisionDao) super.dao;
  }
  
  @GET
  public ResultPage<EditorialDecision> search(@Valid @BeanParam Page page, @BeanParam DecisionSearchRequest req) {
    return dao.search(req, page);
  }

  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@QueryParam("datasetKey") Integer datasetKey,
                              @QueryParam("catalogueKey") Integer catalogueKey,
                              @Context SqlSession session, @Auth ColUser user) {
    Preconditions.checkNotNull(catalogueKey, "catalogueKey parameter is required");
    Preconditions.checkNotNull(datasetKey, "datasetKey parameter is required");
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    int counter = 0;
    for (EditorialDecision d : mapper.processDecisions(catalogueKey, datasetKey)) {
      dao.delete(d.getKey(), user.getKey());
      counter++;
    }
    LOG.info("Deleted {} decisions for dataset {} in catalogue {}", counter, datasetKey, catalogueKey);
  }
}

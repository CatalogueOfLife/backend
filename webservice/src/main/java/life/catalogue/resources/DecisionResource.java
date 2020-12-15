package life.catalogue.resources;

import com.google.common.base.Preconditions;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.RematcherBase;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{key}/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends AbstractDatasetScopedResource<Integer, EditorialDecision, DecisionSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  private final DecisionDao dao;
  
  public DecisionResource(DecisionDao ddao) {
    super(EditorialDecision.class, ddao);
    this.dao = ddao;
  }

  @Override
  ResultPage<EditorialDecision> searchImpl(int datasetKey, DecisionSearchRequest req, Page page) {
    if (req.isSubject()) {
      req.setSubjectDatasetKey(datasetKey);
    } else {
      req.setDatasetKey(datasetKey);
    }
    return dao.search(req, page);
  }

  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int deleteByDataset(@PathParam("key") int projectKey,
                              @QueryParam("datasetKey") Integer datasetKey,
                              @QueryParam("broken") boolean broken,
                              @Context SqlSession session, @Auth User user) {
    Preconditions.checkNotNull(datasetKey, "datasetKey parameter is required");
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    int counter = 0;
    for (EditorialDecision d : mapper.processDecisions(projectKey, datasetKey)) {
      if (!broken || d.getSubject().isBroken()) {
        dao.delete(d.getKey(), user.getKey());
        counter++;
      }
    }
    LOG.info("Deleted {}{} decisions for dataset {} in catalogue {}", counter, broken ? " broken" : "", datasetKey, projectKey);
    return counter;
  }

  @POST
  @Path("/rematch")
  public RematcherBase.MatchCounter rematch(@PathParam("key") int projectKey, DecisionRematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return DecisionRematcher.match(dao, req, user.getKey());
  }

}

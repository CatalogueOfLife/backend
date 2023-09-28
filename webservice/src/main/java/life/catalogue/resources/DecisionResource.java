package life.catalogue.resources;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.RematcherBase;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.dropwizard.auth.Auth;

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
    AtomicInteger counter = new AtomicInteger(0);
    PgUtils.consume(
      () -> mapper.processDecisions(projectKey, datasetKey),
      d -> {
        if (!broken || d.getSubject().isBroken()) {
          dao.delete(d.getKey(), user.getKey());
          counter.incrementAndGet();
        }
      }
    );
    LOG.info("Deleted {}{} decisions for dataset {} in catalogue {}", counter, broken ? " broken" : "", datasetKey, projectKey);
    return counter.get();
  }

  @POST
  @Path("/rematch")
  public RematcherBase.MatchCounter rematch(@PathParam("key") int projectKey, DecisionRematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return DecisionRematcher.match(dao, req, user.getKey());
  }

}

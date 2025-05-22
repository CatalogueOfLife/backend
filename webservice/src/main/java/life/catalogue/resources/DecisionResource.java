package life.catalogue.resources;

import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.RematcherBase;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

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
  FacettedResultPage<EditorialDecision, String> searchImpl(int datasetKey, DecisionSearchRequest req, Page page) {
    if (req.isSubject()) {
      req.setSubjectDatasetKey(datasetKey);
    } else {
      req.setDatasetKey(datasetKey);
    }
    return dao.search(req, page);
  }

  @GET
  @Path("/stale")
  public ResultPage<EditorialDecision> listStaleAmbiguousUpdateDecisions(@PathParam("key") int projectKey, @QueryParam("subjectDatasetKey") Integer subjectDatasetKey) {
    return dao.listStaleAmbiguousUpdateDecisions(projectKey, subjectDatasetKey);
  }

  @DELETE
  @ProjectOnly
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
    LOG.info("Deleted {}{} decisions for dataset {} in project {}", counter, broken ? " broken" : "", datasetKey, projectKey);
    return counter.get();
  }

  @POST
  @Path("/rematch")
  public RematcherBase.MatchCounter rematch(@PathParam("key") int projectKey, DecisionRematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return DecisionRematcher.match(dao, req, user.getKey());
  }

}

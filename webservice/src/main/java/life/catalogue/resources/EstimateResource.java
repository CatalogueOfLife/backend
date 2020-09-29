package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.model.User;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.dao.EstimateDao;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.RematchRequest;
import life.catalogue.matching.decision.RematcherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{datasetKey}/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class EstimateResource extends AbstractDatasetScopedResource<Integer, SpeciesEstimate, EstimateSearchRequest> {
  
  private final EstimateDao dao;
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateResource.class);
  
  public EstimateResource(EstimateDao dao) {
    super(SpeciesEstimate.class, dao);
    this.dao = dao;
  }

  @Override
  ResultPage<SpeciesEstimate> searchImpl(int datasetKey, EstimateSearchRequest req, Page page) {
    req.setDatasetKey(datasetKey);
    return dao.search(req, page);
  }

  @POST
  @Path("/rematch")
  public RematcherBase.MatchCounter rematch(@PathParam("datasetKey") int projectKey, RematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return EstimateRematcher.match(dao, req, user.getKey());
  }
}

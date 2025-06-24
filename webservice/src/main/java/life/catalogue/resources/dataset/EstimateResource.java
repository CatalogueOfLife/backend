package life.catalogue.resources.dataset;

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

import io.dropwizard.auth.Auth;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/estimate")
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
  public RematcherBase.MatchCounter rematch(@PathParam("key") int projectKey, RematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return EstimateRematcher.match(dao, req, user.getKey());
  }
}

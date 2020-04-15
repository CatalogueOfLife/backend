package life.catalogue.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.dao.EstimateDao;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}

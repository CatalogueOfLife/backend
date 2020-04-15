package life.catalogue.resources;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.dao.EstimateDao;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class LEGACYEstimateResource extends LEGACYAbstractDecisionResource<SpeciesEstimate> {

  private final EstimateDao dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LEGACYEstimateResource.class);

  public LEGACYEstimateResource(SqlSessionFactory factory, EstimateDao dao) {
    super(SpeciesEstimate.class, dao, factory);
    this.dao = dao;
  }
  
  @GET
  public ResultPage<SpeciesEstimate> search(@Valid @BeanParam Page page, @BeanParam EstimateSearchRequest req) {
    return dao.search(req, page);
  }
  
}

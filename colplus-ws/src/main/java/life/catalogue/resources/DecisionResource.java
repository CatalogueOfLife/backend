package life.catalogue.resources;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.es.name.index.NameUsageIndexService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  
}

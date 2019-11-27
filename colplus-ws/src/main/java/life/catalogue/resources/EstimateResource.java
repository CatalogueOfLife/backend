package life.catalogue.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.EstimateDao;
import life.catalogue.db.mapper.EstimateMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class EstimateResource extends AbstractDecisionResource<SpeciesEstimate> {
  
  private final EstimateDao dao;
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateResource.class);
  
  public EstimateResource(SqlSessionFactory factory) {
    super(SpeciesEstimate.class, new EstimateDao(factory), factory);
    dao = (EstimateDao) super.dao;
  }
  
  @GET
  public ResultPage<SpeciesEstimate> search(@QueryParam("datasetKey") @DefaultValue(""+Datasets.DRAFT_COL) int datasetKey,
                                      @QueryParam("rank") Rank rank,
                                      @QueryParam("min") Integer min,
                                      @QueryParam("max") Integer max,
                                      @Valid @BeanParam Page page) {
    return dao.search(datasetKey, rank, min, max, page);
  }
  
  @GET
  @Path("/broken")
  public List<SpeciesEstimate> broken(@QueryParam("datasetKey") @DefaultValue(""+Datasets.DRAFT_COL) int datasetKey,
                                      @Context SqlSession session) {
    EstimateMapper mapper = session.getMapper(EstimateMapper.class);
    return mapper.broken(datasetKey);
  }
  
}

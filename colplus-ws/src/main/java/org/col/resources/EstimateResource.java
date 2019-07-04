package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SpeciesEstimate;
import org.col.dao.EstimateDao;
import org.col.db.mapper.EstimateMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class EstimateResource extends GlobalEntityResource<SpeciesEstimate> {
  
  private final EstimateDao dao;
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateResource.class);
  
  public EstimateResource(SqlSessionFactory factory) {
    super(SpeciesEstimate.class, new EstimateDao(factory), factory);
    dao = (EstimateDao) super.dao;
  }
  
  @GET
  public ResultPage<SpeciesEstimate> search(@QueryParam("rank") Rank rank,
                                      @QueryParam("min") Integer min,
                                      @QueryParam("max") Integer max,
                                      @Valid @BeanParam Page page) {
    return dao.search(rank, min, max, page);
  }
  
  @GET
  @Path("/broken")
  public List<SpeciesEstimate> broken(@Context SqlSession session) {
    EstimateMapper mapper = session.getMapper(EstimateMapper.class);
    return mapper.broken();
  }
  
}

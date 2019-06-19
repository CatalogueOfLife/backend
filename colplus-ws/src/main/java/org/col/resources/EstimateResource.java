package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.SpeciesEstimate;
import org.col.dao.EstimateDao;
import org.col.db.mapper.EstimateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class EstimateResource extends GlobalEntityResource<SpeciesEstimate> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateResource.class);
  
  public EstimateResource(SqlSessionFactory factory) {
    super(SpeciesEstimate.class, new EstimateDao(factory), factory);
  }
  
  @GET
  @Path("/broken")
  public List<SpeciesEstimate> broken(@Context SqlSession session) {
    EstimateMapper mapper = session.getMapper(EstimateMapper.class);
    return mapper.broken();
  }
  
}

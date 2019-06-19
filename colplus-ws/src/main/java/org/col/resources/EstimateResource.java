package org.col.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.SpeciesEstimate;
import org.col.dao.SubjectRematcher;
import org.col.dao.EstimateDao;
import org.col.db.mapper.EstimateMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/estimate")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class EstimateResource extends GlobalEntityResource<SpeciesEstimate> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateResource.class);
  
  public EstimateResource(SqlSessionFactory factory) {
    super(SpeciesEstimate.class, new EstimateDao(factory));
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/{key}/rematch")
  public SpeciesEstimate rematch(@PathParam("key") Integer key, @Context SqlSession session, @Auth ColUser user) {
    SpeciesEstimate est = get(key);
    new SubjectRematcher(session).matchEstimate(est);
    session.commit();
    return est;
  }
  
  @GET
  @Path("/broken")
  public List<SpeciesEstimate> broken(@Context SqlSession session) {
    EstimateMapper mapper = session.getMapper(EstimateMapper.class);
    return mapper.broken();
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/broken/rematch")
  public void rematchBroken(@Context SqlSession session, @Auth ColUser user) {
    SubjectRematcher matcher = new SubjectRematcher(session);
    matcher.matchBrokenEstimates();
    session.commit();
  }
  
}

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
import org.col.api.model.EditorialDecision;
import org.col.dao.DecisionDao;
import org.col.dao.SubjectRematcher;
import org.col.db.mapper.DecisionMapper;
import org.col.dw.auth.Roles;
import org.col.es.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends GlobalEntityResource<EditorialDecision> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  
  public DecisionResource(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(EditorialDecision.class, new DecisionDao(factory, indexService));
  }
  
  @GET
  public List<EditorialDecision> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey, @QueryParam("id") String id) {
    return session.getMapper(DecisionMapper.class).listByDataset(datasetKey, id);
  }
  
  @GET
  @Path("/broken")
  public List<EditorialDecision> broken(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    return mapper.subjectBroken(datasetKey);
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/{key}/rematch")
  public EditorialDecision rematch(@PathParam("key") Integer key, @Context SqlSession session, @Auth ColUser user) {
    EditorialDecision ed = get(key);
    new SubjectRematcher(session).matchDecision(ed);
    session.commit();
    return ed;
  }
}

package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.dao.NameDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.util.List;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource extends AbstractDatasetScopedResource<Name> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  private final NameDao dao;

  public NameResource(NameDao dao) {
    super(Name.class, dao);
    this.dao = dao;
  }
  
  @GET
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return dao.homotypicGroup(datasetKey, id);
  }

  @GET
  @Path("{id}/relations")
  public List<NameRelation> getRelations(@PathParam("datasetKey") int datasetKey,
      @PathParam("id") String id, @Context SqlSession session) {
    NameRelationMapper mapper = session.getMapper(NameRelationMapper.class);
    return mapper.list(datasetKey, id);
  }

  @GET
  @Path("{id}/group")
  public List<Name> getIndexGroup(@PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(NameMapper.class).indexGroup(id);
  }

  @GET
  @Path("orphans")
  public ResultPage<Name> listOrphans(@PathParam("datasetKey") int datasetKey,
                                      @QueryParam("before") LocalDateTime before,
                                      @Valid @BeanParam Page page) {
    return dao.listOrphans(datasetKey, before, page);
  }

  @DELETE
  @Path("orphans")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int delete(@PathParam("datasetKey") int datasetKey, @QueryParam("before") LocalDateTime before, @Auth ColUser user, @Context SqlSession session) {
    int cnt = session.getMapper(NameMapper.class).deleteOrphans(datasetKey, before);
    session.commit();
    return cnt;
  }
}

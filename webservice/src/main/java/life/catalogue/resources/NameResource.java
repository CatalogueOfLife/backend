package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.jsr310.LocalDateTimeParam;
import life.catalogue.api.model.*;
import life.catalogue.dao.NameDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/dataset/{key}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource extends AbstractDatasetScopedResource<String, Name, Page> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  private final NameDao dao;

  public NameResource(NameDao dao) {
    super(Name.class, dao);
    this.dao = dao;
  }
  
  @GET
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return dao.homotypicGroup(datasetKey, id);
  }

  @GET
  @Path("{id}/relations")
  public List<NameRelation> getRelations(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.relations(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/types")
  public List<TypeMaterial> getTypeMaterial(@PathParam("key") int datasetKey,
                                         @PathParam("id") String id, @Context SqlSession session) {
    TypeMaterialMapper mapper = session.getMapper(TypeMaterialMapper.class);
    return mapper.listByName(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/group")
  public List<Name> getIndexGroup(@PathParam("id") int id, @Context SqlSession session) {
    return session.getMapper(NameMapper.class).indexGroup(id);
  }

  @GET
  @Path("orphans")
  public ResultPage<Name> listOrphans(@PathParam("key") int datasetKey,
                                      @QueryParam("before") LocalDateTimeParam before,
                                      @Valid @BeanParam Page page) {
    return dao.listOrphans(datasetKey, before==null ? null : before.get(), page);
  }

  @DELETE
  @Path("orphans")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int delete(@PathParam("key") int datasetKey,
                    @QueryParam("before") LocalDateTimeParam before,
                    @Auth User user) {
    return dao.deleteOrphans(datasetKey, before==null ? null : before.get(), user);
  }
}

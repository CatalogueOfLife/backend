package life.catalogue.resources.parser;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.dw.auth.Roles;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/parser/name/config")
@Produces(MediaType.APPLICATION_JSON)
public class NameParserAdminResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameParserAdminResource.class);
  private final ParserConfigDao dao;

  public NameParserAdminResource(SqlSessionFactory factory) {
    dao = new ParserConfigDao(factory);
  }

  @GET
  public ResultPage<ParserConfig> searchConfig(@BeanParam QuerySearchRequest request, @Valid @BeanParam Page page) {
    return dao.search(request, page);
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  public String createConfig(@Valid ParserConfig config, @Auth User user) {
    dao.add(config, user.getKey());
    return config.getId();
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  @Path("batch")
  public List<String> createConfigs(@Valid List<ParserConfig> configs, @Auth User user) {
    List<String> ids = new ArrayList<>(configs.size());
    for (ParserConfig pc : configs) {
      if (pc == null) continue;
      dao.add(pc, user.getKey());
      ids.add(pc.getId());
    }
    return ids;
  }

  @GET
  @Path("{id}")
  public ParserConfig getConfig(@PathParam("id") String id) {
    return dao.get(id);
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN})
  public void deleteConfig(@PathParam("id") String id, @Auth User user) {
    dao.deleteName(id, user.getKey());
  }

}

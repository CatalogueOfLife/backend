package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.PublisherDao;
import life.catalogue.dao.UserDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.AuthFilter;
import life.catalogue.dw.auth.IdentityService;
import life.catalogue.dw.auth.JwtCodec;
import life.catalogue.dw.auth.Roles;
import life.catalogue.gbifsync.GbifRegistryCache;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
  
  private final JwtCodec jwt;
  private final UserDao dao;
  private final IdentityService idService;
  private final GbifRegistryCache registry;
  private final PublisherDao pdao;

  public UserResource(JwtCodec jwt, UserDao dao, IdentityService idService, GbifRegistryCache registry, PublisherDao pdao) {
    this.jwt = jwt;
    this.dao = dao;
    this.idService = idService;
    this.registry = registry;
    this.pdao = pdao;
  }

  /**
   * obfuscate email and personal things
   */
  private static User obfuscate(User u) {
    u.setEmail(null);
    u.setSettings(null);
    u.setLastLogin(null);
    return u;
  }

  @GET
  public ResultPage<User> search(@QueryParam("q") String q, @QueryParam("role") User.Role role, @Valid @BeanParam Page page, @Auth User admin) {
    ResultPage<User> users = dao.search(q, role, page);
    if (!admin.isAdmin()) {
      users.forEach(UserResource::obfuscate);
    }
    return users;
  }

  @GET
  @Path("/{key}")
  public User get(@PathParam("key") Integer key, @Auth Optional<User> admin) {
    User user = getUser(key);
    // obfuscate email and personal things if its not an admin
    if (admin.isEmpty() || !admin.get().isAdmin()) {
      obfuscate(user);
    }
    return user;
  }

  @GET
  @Path("/me")
  public User me(@Auth User user) {
    // add release keys to editor/reviewer props for the UI only.
    // See https://github.com/CatalogueOfLife/checklistbank/issues/1268
    dao.addReleaseKeys(user);
    return user;
  }
  
  /**
   * Makes sure a user has authenticated with BasicAuth and then returns a new JWT token if successful.
   */
  @GET
  @Path("/login")
  public String login(@Context SecurityContext secCtxt, @Auth User user) {
    // the user shall be authenticated using basic auth scheme only.
    if (secCtxt == null || !SecurityContext.BASIC_AUTH.equalsIgnoreCase(secCtxt.getAuthenticationScheme())) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    if (user == null) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    return jwt.generate(user);
  }

  /**
   * Invalidates the JWT token and logs out.
   */
  @DELETE
  @Path("/logout")
  public void logout(@Auth User user, @Context ContainerRequestContext req) {
    final String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    jwt.invalidate(auth);
  }
  
  @PUT
  @Path("/settings")
  public void updateSettings(Map<String, String> settings, @Auth User user) {
    dao.updateSettings(settings, user);
  }

  @GET
  @Path("/dataset")
  public List<Dataset> datasets(@Auth User user, @QueryParam("origin") DatasetOrigin origin, @Context SqlSession session) {
    final DatasetMapper dm = session.getMapper(DatasetMapper.class);
    // this never yields releases
    return user.getEditor().intStream()
               .filter(dk -> {
                 if (origin != null) {
                   return DatasetInfoCache.CACHE.info(dk).origin == origin;
                 } else {
                   return true;
                 }
               })
               .boxed()
               .map(dm::get)
               .collect(Collectors.toList());
  }

  @GET
  @Path("/dataset/{key}")
  public boolean hasReadAccess(@PathParam("key") int key, @Auth User user) {
    return AuthFilter.hasReadAccess(user, key);
  }

  @GET
  @Path("/dataset/{key}/write")
  public boolean hasWriteAccess(@PathParam("key") int key, @Auth User user) {
    return AuthFilter.hasWriteAccess(user, key);
  }

  @PUT
  @Path("/{key}/role")
  @RolesAllowed({Roles.ADMIN})
  public void changeRole(@PathParam("key") int key, @Auth User admin, List<User.Role> roles) {
    dao.changeRoles(key, admin, roles);
  }

  @DELETE
  @Path("/{key}/role/{role}")
  @RolesAllowed({Roles.ADMIN})
  public void changeRole(@PathParam("key") int key, @PathParam("role") User.Role role, @Auth User admin) {
    dao.revokeRoleOnAllDatasets(key, admin, role);
  }

  /**
   * Lets the user edit all external datasets published by a GBIF organisation.
   */
  @POST
  @Path("/{key}/orgEditor/{publisherKey}")
  @RolesAllowed({Roles.ADMIN})
  public void addOrgEditor(@PathParam("key") int key, @PathParam("publisherKey") UUID publisherKey, @Auth User admin) {
    storeOrganisation(publisherKey);
    dao.changeOrgEditor(key, admin, publisherKey, true);
  }

  @DELETE
  @Path("/{key}/orgEditor/{publisherKey}")
  @RolesAllowed({Roles.ADMIN})
  public void removeOrgEditor(@PathParam("key") int key, @PathParam("publisherKey") UUID publisherKey, @Auth User admin) {
    dao.changeOrgEditor(key, admin, publisherKey, false);
  }

  /**
   * Lets the user register datasets in GBIF under a GBIF organisation. Grant it only once the organisation agreed.
   */
  @POST
  @Path("/{key}/orgPublisher/{publisherKey}")
  @RolesAllowed({Roles.ADMIN})
  public void addOrgPublisher(@PathParam("key") int key, @PathParam("publisherKey") UUID publisherKey, @Auth User admin) {
    storeOrganisation(publisherKey);
    dao.changeOrgPublisher(key, admin, publisherKey, true);
  }

  @DELETE
  @Path("/{key}/orgPublisher/{publisherKey}")
  @RolesAllowed({Roles.ADMIN})
  public void removeOrgPublisher(@PathParam("key") int key, @PathParam("publisherKey") UUID publisherKey, @Auth User admin) {
    dao.changeOrgPublisher(key, admin, publisherKey, false);
  }

  /**
   * Makes sure the organisation exists in GBIF and stores it, so its title can be shown for the granted role.
   */
  private void storeOrganisation(UUID publisherKey) {
    Publisher p;
    try {
      p = registry.publisherEntity(publisherKey);
    } catch (WebApplicationException e) {
      if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
        throw new IllegalArgumentException("GBIF organisation " + publisherKey + " does not exist");
      }
      throw e;
    }
    if (p == null) {
      throw new IllegalArgumentException("GBIF organisation " + publisherKey + " does not exist");
    }
    pdao.upsert(p);
  }

  @POST
  @Path("/{key}/block")
  @RolesAllowed({Roles.ADMIN})
  public void block(@PathParam("key") int key, @Auth User admin) {
    dao.block(key, admin);
  }

  @DELETE
  @Path("/{key}/block")
  @RolesAllowed({Roles.ADMIN})
  public void unblock(@PathParam("key") int key, @Auth User admin) {
    dao.unblock(key, admin);
  }

  private User getUser(int key) throws NotFoundException {
    User user = dao.get(key);
    if (user == null) {
      throw NotFoundException.notFound(User.class, key);
    }
    return user;
  }

  @DELETE
  @Path("/cache")
  @RolesAllowed({Roles.ADMIN})
  public int flushCache() throws Exception {
    return idService.flushCache();
  }
}

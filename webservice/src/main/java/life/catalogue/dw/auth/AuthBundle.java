package life.catalogue.dw.auth;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.*;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * Wires up authentication against the GBIF registry and authorization based on CoL user roles.
 * Sets up request filters to authenticate any request if an Authorization header is given.
 * <p/>
 * Authorization is done in 2 parts:
 *  - a filter to protect private datasets by inspecting the requested URI.
 *  - a dynamic feature that requires some authenticated user if a non Optional @Auth annotation is present on a method
 *  - the {@RolesAllowedDynamicFeature} that makes sure required user roles do exist on specifically annotated methods
 */
public class AuthBundle implements ConfiguredBundle<WsServerConfig>, UserListener, DatasetListener {
  
  private JwtCodec jwtCodec;
  private IdentityService idService;
  private PrivateFilter privateFilter;
  
  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    jwtCodec = new JwtCodec(cfg.jwtKey);
    idService = new IdentityService(cfg.auth.createAuthenticationProvider());
    privateFilter = new PrivateFilter();

    // authentication - both basic & JWT
    environment.jersey().register(new AuthFilter(idService, jwtCodec, cfg.requireSSL));
    // inject user via @Auth
    environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
    // require authenticated user for methods with @Auth
    environment.jersey().register(RequireAuthDynamicFeature.class);
    // require specific roles annotation
    environment.jersey().register(RolesAllowedDynamicFeature2.class);
    // check access to private datasets based on URI
    environment.jersey().register(privateFilter);
  }
  
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }

  public JwtCodec getJwtCodec() {
    return jwtCodec;
  }

  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.privateFilter.setSqlSessionFactory(factory);
    this.idService.setSqlSessionFactory(factory);
  }

  public void setClient(CloseableHttpClient http) {
    this.idService.setClient(http);
  }

  public IdentityService getIdService() {
    return idService;
  }

  @Override
  public void userPermissionChanged(UserPermissionChanged event){
    idService.invalidate(event.username);
  }

  @Override
  public void userChanged(UserChanged event){
    String username = event.isDeletion() ? event.old.getUsername() : event.obj.getUsername();
    idService.invalidate(username);
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      privateFilter.updateCache(event.key, false);
      // remove all users from the cache that are on the acl lists of this dataset
      for (var uname : event.usernamesToInvalidate) {
        idService.invalidate(uname);
      }
    } else {
      privateFilter.updateCache(event.key, event.obj.isPrivat());
    }
    // a new dataset, make creator an editor unless it is a release or a bot
    if (event.isCreated()
        && event.obj.getCreatedBy() != null
        && !Users.isBot(event.obj.getCreatedBy())
        && !event.obj.getOrigin().isRelease()) {
      User creator = idService.get(event.obj.getCreatedBy());
      creator.addDatasetRole(User.Role.EDITOR, event.key);
    }
  }

  public void flushCache() {
    privateFilter.flushCache();
    idService.flushCache();
  }
}

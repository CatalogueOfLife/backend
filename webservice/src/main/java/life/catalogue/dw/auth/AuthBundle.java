package life.catalogue.dw.auth;

import com.google.common.eventbus.Subscribe;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.UserChanged;
import life.catalogue.api.event.UserPermissionChanged;
import life.catalogue.api.model.User;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

/**
 * Wires up authentication against the GBIF registry and authorization based on CoL user roles.
 * Sets up request filters to authenticate any request if an Authorization header is given.
 * <p/>
 * Authorization is done in 2 parts:
 *  - a filter to protect private datasets by inspecting the requested URI.
 *  - a dynamic feature that reuires some authenticated user if a non Optional @Auth annotation is present on a method
 *  - the {@RolesAllowedDynamicFeature} that makes sure required user roles do exist on specifically annotated methods
 */
public class AuthBundle implements ConfiguredBundle<WsServerConfig> {
  
  private JwtCodec jwtCodec;
  private IdentityService idService;
  private PrivateFilter privateFilter;
  
  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    environment.jersey().register(RolesAllowedDynamicFeature.class);
    
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
    environment.jersey().register(RolesAllowedDynamicFeature.class);
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

  @Subscribe
  public void permissionChanged(UserPermissionChanged event){
    idService.invalidate(event.username);
  }

  @Subscribe
  public void userChanged(UserChanged event){
    idService.invalidate(event.username);
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      privateFilter.updateCache(event.key, false);
    } else {
      privateFilter.updateCache(event.key, event.obj.isPrivat());
    }
  }

}

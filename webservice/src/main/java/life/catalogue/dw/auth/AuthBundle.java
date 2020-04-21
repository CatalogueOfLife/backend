package life.catalogue.dw.auth;

import com.google.common.eventbus.Subscribe;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.UserChanged;
import life.catalogue.api.model.User;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

/**
 * Wires up authentication against the GBIF registry and authorization based on CoL user roles.
 * Apart from the base root of the API ALL requests including OPTION and GET will have to provide authentication!
 */
public class AuthBundle implements ConfiguredBundle<WsServerConfig> {
  
  private JwtCodec jwtCodec;
  private IdentityService idService;
  private PrivateFilter privateFilter;
  
  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    environment.jersey().register(RolesAllowedDynamicFeature.class);
    
    jwtCodec = new JwtCodec(cfg.jwtKey);
    idService = new IdentityService(cfg.auth.createAuthenticationProvider(), true);
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
  public void userChanged(UserChanged event){
    if (event.isDeletion()) {
      idService.removeFromCache(event.username);
    } else {
      idService.cache(event.obj);
    }
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      privateFilter.updateCache(event.obj.getKey(), false);
    } else {
      if (event.obj.getKey() != null) {
        privateFilter.updateCache(event.obj.getKey(), event.obj.isPrivat());
      }
    }
  }

}

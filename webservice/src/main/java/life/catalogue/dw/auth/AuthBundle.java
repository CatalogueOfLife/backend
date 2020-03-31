package life.catalogue.dw.auth;

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ColUser;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.ws.rs.container.ContainerRequestFilter;

public class AuthBundle implements ConfiguredBundle<WsServerConfig> {
  
  private JwtCodec jwtCodec;
  private IdentityService idService;
  
  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    environment.jersey().register(RolesAllowedDynamicFeature.class);
    
    jwtCodec = new JwtCodec(cfg.jwtKey);
    idService = new IdentityService(cfg.auth.createAuthenticationProvider(), true);

    ContainerRequestFilter authFilter = new AuthFilter(idService, jwtCodec);
    // WARNING!!! Never check in the LocalAuthFilter. It is meant purely for local testing !!!
    //authFilter = new LocalAuthFilter();
    environment.jersey().register(new AuthDynamicFeature(authFilter));
    environment.jersey().register(new AuthValueFactoryProvider.Binder<>(ColUser.class));
  }
  
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }
  
  public IdentityService getIdentityService() {
    return idService;
  }
  
  public JwtCodec getJwtCodec() {
    return jwtCodec;
  }
}

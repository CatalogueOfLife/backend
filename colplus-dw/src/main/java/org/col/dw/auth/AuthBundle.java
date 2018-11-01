package org.col.dw.auth;

import com.google.common.collect.Lists;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.UnauthorizedHandler;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.chained.ChainedAuthFilter;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.api.model.ColUser;
import org.col.dw.PgAppConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

public class AuthBundle implements ConfiguredBundle<PgAppConfig> {
  public static final String REALM = "COL";
  
  private JwtCoder jwtCoder;
  private IdentityService idService;
  
  @Override
  public void run(PgAppConfig configuration, Environment environment) throws Exception {
    environment.jersey().register(RolesAllowedDynamicFeature.class);
  
    jwtCoder = new JwtCoder(configuration.auth);
    idService = new IdentityService(configuration.auth);
    //Security configuration
    UnauthorizedHandler unauthorizedHandler = new ColUnauthorizedHandler();
    BasicCredentialAuthFilter<ColUser> basicAuthFilter = new BasicCredentialAuthFilter.Builder<ColUser>()
        .setAuthenticator(idService)
        .setRealm(REALM)
        .setUnauthorizedHandler(unauthorizedHandler)
        .buildAuthFilter();
    
    JwtCredentialsFilter jwtAuthFilter = new JwtCredentialsFilter.Builder()
        .setAuthenticator(new JwtAuthenticator(jwtCoder, idService))
        .setRealm(REALM)
        .setUnauthorizedHandler(unauthorizedHandler)
        .buildAuthFilter();
    
    environment.jersey().register(new AuthDynamicFeature(new ChainedAuthFilter(
        Lists.newArrayList(jwtAuthFilter, basicAuthFilter)
    )));
    environment.jersey().register(new AuthValueFactoryProvider.Binder<>(ColUser.class));
  
    //Health check
    //environment.healthChecks().register("UserService", new AuthenticatorHealthCheck(authenticator));
  }
  
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }
  
  public IdentityService getIdentityService() {
    return idService;
  }
  
  public JwtCoder getJwtCoder() {
    return jwtCoder;
  }
}

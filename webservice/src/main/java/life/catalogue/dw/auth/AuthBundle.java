package life.catalogue.dw.auth;

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ColUser;
import life.catalogue.api.model.Dataset;
import life.catalogue.dw.auth.gbif.GBIFAuthentication;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.ws.rs.container.ContainerRequestFilter;
import java.util.Map;

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

    ContainerRequestFilter authFilter = new AuthFilter(idService, jwtCodec, cfg.requireSSL);
    // WARNING!!! Never check in the LocalAuthFilter. It is meant purely for local testing !!!
    //authFilter = new LocalAuthFilter();
    environment.jersey().register(new AuthDynamicFeature(authFilter));
    environment.jersey().register(new AuthValueFactoryProvider.Binder<>(ColUser.class));
    environment.jersey().register(privateFilter);
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

  public void updateUser(ColUser user){
    if (user != null) {
      idService.cache(user);
    }
  }

  public void updateDatasetPrivacy(Map<Integer, Boolean> datasetPrivacy){
    if (datasetPrivacy != null) {
      for (Map.Entry<Integer, Boolean> d : datasetPrivacy.entrySet()) {
        if (d.getKey() != null && d.getValue() != null) {
          privateFilter.updateCache(d.getKey(), d.getValue());
        }
      }
    }
  }
}

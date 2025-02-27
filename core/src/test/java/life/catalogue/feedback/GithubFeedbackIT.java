package life.catalogue.feedback;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DSID;
import life.catalogue.doi.service.UserAgentFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

@Ignore("manual github API test")
public class GithubFeedbackIT {
  Client client;

  @Before
  public void setup() throws IOException {
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider(ApiModule.MAPPER);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new UserAgentFilter());
    cfg.property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
       .property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);
    client = ClientBuilder.newClient(cfg);
  }

  @After
  public void clear() throws Exception {
    client.close();
  }

  @Test
  public void test() throws Exception {
    var cfg = new GithubConfig();
    cfg.organisation = "CatalogueOfLife";
    cfg.repository = "data";
    cfg.token = "xxx";
    cfg.labels = List.of("testing");

    var gh = new GithubFeedback(cfg, URI.create("http://test.org"), client, null);
    var issue = gh.create(Optional.empty(), DSID.of(3,"ASDFG"), "Foo bar");
    System.out.println(issue);
  }

}
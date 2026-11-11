package life.catalogue.gbifsync;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.GbifConfig;

import java.net.URI;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import static org.junit.Assert.*;

public class GbifRegistryClientTest {
  FakeGbifRegistry gbif;
  Client client;
  GbifRegistryClient registry;

  @Before
  public void init() throws Exception {
    gbif = new FakeGbifRegistry();
    client = ClientBuilder.newClient();
    registry = new GbifRegistryClient(client, gbif.config());
  }

  @After
  public void close() {
    client.close();
    gbif.close();
  }

  @Test
  public void roundtrip() throws Exception {
    var d = ApiModule.MAPPER.createObjectNode();
    d.put("type", "CHECKLIST");
    d.put("title", "Ants");
    UUID key = registry.createDataset(d);
    assertEquals("Ants", gbif.datasets.get(key).path("title").asText());

    var g = registry.getDataset(key);
    assertEquals(key.toString(), g.path("key").asText());
    g.put("title", "Ants of Brandenburg");
    registry.updateDataset(g);
    assertEquals("Ants of Brandenburg", gbif.datasets.get(key).path("title").asText());

    registry.addEndpoint(key, "COLDP", URI.create("https://api.checklistbank.org/dataset/1/archive.zip"));
    var endpoints = registry.listEndpoints(key);
    assertEquals(1, endpoints.size());
    assertEquals("COLDP", endpoints.get(0).type());
    registry.deleteEndpoint(key, endpoints.get(0).key());
    assertTrue(registry.listEndpoints(key).isEmpty());

    registry.addIdentifier(key, "CLB_DATASET_KEY", "1");
    var ids = registry.listIdentifiers(key);
    assertEquals("1", ids.get(0).identifier());
    registry.deleteIdentifier(key, ids.get(0).key());
    assertTrue(registry.listIdentifiers(key).isEmpty());

    registry.crawl(key);
    assertEquals(1, gbif.crawls(key));

    assertTrue(registry.deleteDataset(key));
    assertNull(registry.getDataset(key));
    assertFalse(registry.deleteDataset(key));
  }

  @Test
  public void errors() throws Exception {
    UUID key = UUID.randomUUID();
    gbif.failNext = 503;
    assertThrows(UnavailableException.class, () -> registry.getDataset(key));
    gbif.failNext = 429;
    assertThrows(TooManyRequestsException.class, () -> registry.getDataset(key));
    gbif.failNext = 400;
    var e = assertThrows(GbifRegistryClient.GbifRegistryException.class, () -> registry.crawl(key));
    assertTrue(e.isClientError());
    // a missing dataset is an error unless the call expects it
    e = assertThrows(GbifRegistryClient.GbifRegistryException.class, () -> registry.crawl(key));
    assertEquals(404, e.getStatus());

    // wrong credentials
    GbifConfig cfg = gbif.config();
    cfg.password = "wrong";
    var wrong = new GbifRegistryClient(client, cfg);
    e = assertThrows(GbifRegistryClient.GbifRegistryException.class, () -> wrong.getDataset(key));
    assertEquals(401, e.getStatus());

    // no credentials
    cfg.username = null;
    var none = new GbifRegistryClient(client, cfg);
    assertFalse(none.hasCredentials());
    assertThrows(IllegalStateException.class, () -> none.getDataset(key));

    // unreachable
    cfg = gbif.config();
    gbif.close();
    var gone = new GbifRegistryClient(client, cfg);
    assertThrows(UnavailableException.class, () -> gone.getDataset(key));
  }
}

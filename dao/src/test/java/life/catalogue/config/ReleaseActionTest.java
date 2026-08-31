package life.catalogue.config;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReleaseActionTest {

  /**
   * A hook whose endpoint moved answers 404 rather than throwing, so the status is the only thing that
   * tells a failed action from a working one. Callers go through callAll, which warns on anything but 2xx.
   */
  @Test
  public void success() {
    assertTrue(ReleaseAction.isSuccess(200));
    assertTrue(ReleaseAction.isSuccess(201));
    assertTrue(ReleaseAction.isSuccess(204));
    assertFalse(ReleaseAction.isSuccess(301));
    assertFalse(ReleaseAction.isSuccess(401));
    assertFalse(ReleaseAction.isSuccess(404)); // the endpoint moved
    assertFalse(ReleaseAction.isSuccess(500));
    assertFalse(ReleaseAction.isSuccess(-1));  // the call itself blew up
  }

  @Test
  public void call() {
    var httpClient = HttpClientBuilder.create().build();

    var d = new Dataset();
    d.setKey(987654321);
    d.setSourceKey(3);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setAlias("COL24 XR");
    d.setTitle("Catalogue of Life");
    d.setPublisher(Agent.organisation("Catalogue of Life"));

    var act = new ReleaseAction();
    act.method = "GET";
    act.url = "http://www.checklistbank.org/dataset/{key}?alias={ALIAS}";
    assertEquals(200, act.call(httpClient, d));
  }
}
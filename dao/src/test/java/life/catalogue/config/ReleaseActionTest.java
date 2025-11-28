package life.catalogue.config;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReleaseActionTest {

  @Test
  public void call() {
    var httpClient = HttpClientBuilder.create().build();

    var d = new Dataset();
    d.setKey(1234);
    d.setSourceKey(3);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setAlias("COL24 XR");
    d.setTitle("Catalogue of Life");
    d.setPublisher(Agent.organisation("Catalogue of Life"));

    var act = new ReleaseAction();
    act.method = "GET";
    act.url = "http://www.google.com/search/{key}?alias={ALIAS}";
    assertEquals(404, act.call(httpClient, d));
  }
}
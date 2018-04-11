package org.col.dw.anystyle;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.lifecycle.Managed;

public class AnystyleParserWrapper implements Managed {

  public static AnystyleParserWrapper getInstance() {
    if (instance == null) {
      throw new IllegalStateException("AnystyleParserWrapper not initialized yet");
    }
    return instance;
  }

  private static AnystyleParserWrapper instance;

  private final AnystyleWebService svc;
  private final CloseableHttpClient hc;
  private final ObjectMapper om;

  private AnystyleParserWrapper(CloseableHttpClient hc) {
    this.svc = new AnystyleWebService();
    this.hc = hc;
    this.om = new ObjectMapper();
  }

  public synchronized Optional<ObjectNode> parse(String ref) {
    if (StringUtils.isAllBlank(ref)) {
      return Optional.empty();
    }
    try (CloseableHttpResponse response = hc.execute(request(ref))) {
      String json = EntityUtils.toString(response.getEntity());
      JsonNode node = om.readTree(json);
      if (node.isArray()) {
        ObjectNode on = (ObjectNode) (((ArrayNode) node).get(0));
        return Optional.of(on);
      }
      throw new RuntimeException("Unexpected Anystyle response (expected array)");
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void start() throws Exception {
    this.svc.start();
    instance = this;
  }

  @Override
  public void stop() throws Exception {
    this.svc.stop();
  }

  private static HttpGet request(String reference) throws URISyntaxException {
    URIBuilder ub = new URIBuilder();
    ub.setScheme("http");
    ub.setHost("localhost");
    ub.setPort(AnystyleWebService.HTTP_PORT);
    ub.setParameter(AnystyleWebService.QUERY_PARAM_REF, reference);
    return new HttpGet(ub.build());
  }

}

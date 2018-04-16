package org.col.dw.anystyle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.api.model.CslItemData;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.dropwizard.lifecycle.Managed;

public class AnystyleParserWrapper implements Managed, AutoCloseable {

  /*
   * This is not a regular static factory method. It never creates an instance of
   * AnystyleParserWrapper. The returned instance is presumed to have been created already by
   * DropWizard or something else managing the life cycle of an AnystyleParserWrapper.
   */
  public static AnystyleParserWrapper getInstance() {
    if (instance == null) {
      throw new IllegalStateException("AnystyleParserWrapper not initialized yet");
    }
    return instance;
  }

  private static final TypeReference<List<Map<String, Object>>> ANYSTYLE_RESPONSE_TYPE =
      new TypeReference<List<Map<String, Object>>>() {};

  private static AnystyleParserWrapper instance;

  private final AnystyleWebService svc;
  private final CloseableHttpClient hc;
  private final ObjectMapper om;

  public AnystyleParserWrapper(CloseableHttpClient hc) {
    this.svc = new AnystyleWebService();
    this.hc = hc;
    this.om = new ObjectMapper();
    this.om.setSerializationInclusion(Include.NON_NULL);
  }

  public CslItemData parse(String ref) {
    if (Strings.isNullOrEmpty(ref)) {
      return new CslItemData();
    }
    try (CloseableHttpResponse response = hc.execute(request(ref))) {
      InputStream in = response.getEntity().getContent();
      List<Map<String, Object>> raw = om.readValue(in, ANYSTYLE_RESPONSE_TYPE);
      if (raw.size() != 1) {
        throw new RuntimeException("Unexpected response from Anystyle");
      }
      Map<String, Object> map = raw.get(0);
      // Copy keys to prevent ConcurrentModificationException
      for (String key : new ArrayList<>(map.keySet())) {
        if (key.indexOf('-') != -1) {
          map.put(toCamelCase(key), map.remove(key));
        }
      }
      return om.convertValue(map, CslItemData.class);
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void start() throws Exception {
    svc.start();
    instance = this;
  }

  @Override
  public synchronized void stop() throws Exception {
    svc.stop();
  }

  @Override
  public void close() throws Exception {
    stop();
  }

  private static HttpGet request(String reference) throws URISyntaxException {
    URIBuilder ub = new URIBuilder();
    ub.setScheme("http");
    ub.setHost("localhost");
    ub.setPort(AnystyleWebService.HTTP_PORT);
    ub.setParameter(AnystyleWebService.QUERY_PARAM_REF, reference);
    return new HttpGet(ub.build());
  }

  private static String toCamelCase(String key) {
    StringBuilder sb = new StringBuilder(key.length());
    boolean hyphen = false;
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) == '-') {
        hyphen = true;
      } else {
        if (hyphen) {
          sb.append(Character.toUpperCase(key.charAt(i)));
          hyphen = false;
        } else {
          sb.append(key.charAt(i));
        }
      }
    }
    return sb.toString();
  }

}

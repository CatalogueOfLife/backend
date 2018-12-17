package org.col.csl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Strings;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.col.admin.config.AnystyleConfig;
import org.col.api.model.CslData;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.api.jackson.ApiModule.MAPPER;

/*
 * Passes citations on to a (Ruby/sinatra) web service which uses Anystyle to parse the citations
 * and returns a queue of CslData objects (JSON-formatted). The assumption is that the queue will
 * contain excatly one CslData object.
 */
@Deprecated
public class AnystyleParserWrapper implements Parser<CslData> {
  
  private static final Logger LOG = LoggerFactory.getLogger(AnystyleParserWrapper.class);
  
  private static final TypeReference<List<CslData>> ANYSTYLE_RESPONSE_TYPE =
      new TypeReference<List<CslData>>() {
      };
  
  private final CloseableHttpClient hc;
  private final AnystyleConfig cfg;
  private final Timer timer;
  
  public AnystyleParserWrapper(CloseableHttpClient hc, AnystyleConfig cfg, MetricRegistry metrics) {
    this.hc = hc;
    this.cfg = cfg;
    this.timer = metrics.timer("org.col.parser.anystyle");
  }
  
  public Optional<CslData> parse(String ref) throws UnparsableException {
    if (Strings.isNullOrEmpty(ref)) {
      return Optional.empty();
    }
    String json = null;
    try (Timer.Context ctx = timer.time();
         CloseableHttpResponse response = hc.execute(request(ref))
    ) {
      json = EntityUtils.toString(response.getEntity());
      List<CslData> raw;
      try {
        raw = MAPPER.readValue(json, ANYSTYLE_RESPONSE_TYPE);
      } catch (JsonMappingException e) {
        String msg = "Anystyle response not deserializable into List<CslData>: " + e.getMessage();
        String err = getError(ref, msg, json);
        LOG.error(err);
        throw new UnparsableException(err);
      }
      if (raw.size() != 1) {
        String msg = String.format("Anystyle result is queue of size %s (expected 1)", raw.size());
        String err = getError(ref, msg, json);
        LOG.error(err);
        throw new UnparsableException(err);
      }
      CslData csl = raw.get(0);
      csl.setId(null);
      return Optional.of(csl);
      
    } catch (IOException | URISyntaxException e) {
      String err = getError(ref, e.getMessage(), json);
      LOG.error(err);
      throw new UnparsableException(err);
      
    }
  }
  
  private static String getError(String ref, String errMsg, String json) {
    StringBuilder sb = new StringBuilder(100);
    sb.append("Error parsing citation: \"");
    sb.append(ref);
    sb.append("\": ");
    sb.append(errMsg);
    sb.append(".");
    if (json != null) {
      sb.append(" Anystyle response was: ");
      sb.append(json);
    }
    return sb.toString();
  }
  
  private HttpGet request(String reference) throws URISyntaxException {
    URIBuilder ub = new URIBuilder(cfg.baseUrl);
    ub.setParameter("ref", reference);
    return new HttpGet(ub.build());
  }
  
}

package org.col.csl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.api.jackson.ApiModule.MAPPER;

/*
 * Passes citations on to a (Ruby/sinatra) web service which uses Anystyle to parse the citations
 * and returns a list of CslData objects (JSON-formatted). The assumption is that the list will
 * contain excatly one CslData object.
 */
public class AnystyleParserWrapper implements Parser<CslData> {

  private static final Logger LOG = LoggerFactory.getLogger(AnystyleParserWrapper.class);

  private static final TypeReference<List<CslData>> ANYSTYLE_RESPONSE_TYPE =
      new TypeReference<List<CslData>>() {};

  private final CloseableHttpClient hc;
  private final AnystyleConfig cfg;

  public AnystyleParserWrapper(CloseableHttpClient hc, AnystyleConfig cfg) {
    this.hc = hc;
    this.cfg = cfg;
  }

  public Optional<CslData> parse(String ref) {
    if (Strings.isNullOrEmpty(ref)) {
      return Optional.empty();
    }
    try (CloseableHttpResponse response = hc.execute(request(ref))) {
      String json = EntityUtils.toString(response.getEntity());
      List<CslData> raw;
      try {
        raw = MAPPER.readValue(json, ANYSTYLE_RESPONSE_TYPE);
      } catch (JsonMappingException e) {
        String err = String.format(
            "Error parsing citation: \"%s\". Anystyle JSON output could not be deserialized into List<CslData>: %s",
            ref, json);
        LOG.error(err);
        throw new RuntimeException(err, e);
      }
      if (raw.size() != 1) {
        LOG.error("Anystyle result is list of size {}", raw.size());
        throw new RuntimeException("Unexpected response from Anystyle");
      }
      return Optional.of(raw.get(0));
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpGet request(String reference) throws URISyntaxException {
    URIBuilder ub = new URIBuilder();
    ub.setScheme("http");
    ub.setHost(cfg.host);
    ub.setPort(cfg.port);
    ub.setParameter("ref", reference);
    return new HttpGet(ub.build());
  }

}

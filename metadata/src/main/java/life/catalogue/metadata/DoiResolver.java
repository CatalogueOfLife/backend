package life.catalogue.metadata;

import com.fasterxml.jackson.databind.ObjectReader;

import de.undercouch.citeproc.csl.CSLType;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;

import java.io.IOException;
import java.util.List;

import life.catalogue.metadata.eml.EmlParser;

import life.catalogue.parser.CSLTypeParser;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CrossRef DOI resolver that can return citation metadata for most (all?) DOIs.
 */
public class DoiResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DoiResolver.class);
  private static final String CSL_TYPE = "application/vnd.citationstyles.csl+json";
  private CloseableHttpClient http;
  private ObjectReader reader;
  public DoiResolver(CloseableHttpClient http) {
    this.http = http;
    reader = ApiModule.MAPPER.readerFor(CrossRefCitation.class);
  }

  public Citation resolve(DOI doi) {
    HttpGet request = new HttpGet(doi.getUrl());
    request.addHeader(HttpHeaders.ACCEPT, CSL_TYPE);

    try (var resp = http.execute(request)) {
      if (resp.getStatusLine().getStatusCode() / 100 == 2) {
        HttpEntity entity = resp.getEntity();
        if (entity != null) {
          // return it as a String
          String json = EntityUtils.toString(entity);
          // lower case "ORCID" author properties to fit our models easily
          json = json.replaceAll("\"ORCID\":", "\"orcid\":");
          Citation c = reader.readValue(json);
          c.setId(doi.getDoiName());
          c.setDoi(doi);
          return c;
        }
      } else {
        LOG.warn("Failed to resolve DOI {}. HTTP {}", doi, resp.getStatusLine());
      }

    } catch (IOException e) {
      LOG.error("Error resolving DOI {}", doi, e);
    }

    return null;
  }

  public static class CrossRefCitation extends Citation {

    public void setISSN(List<String> issn) {
      if (issn == null || issn.isEmpty()) {
        setIssn(null);
      } else {
        setIssn(issn.get(0));
      }
    }

    public void setType(String type) {
      var ct = CSLTypeParser.PARSER.parseOrNull(type);
      super.setType(ct);
    }
  }
}

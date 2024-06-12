package life.catalogue.metadata;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.parser.CSLTypeParser;

import java.io.IOException;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectReader;

/**
 * A CrossRef DOI resolver that can return citation metadata for most (all?) DOIs.
 * Crossref apparently transforms requests to use their v1 API which sometimes is down: https://status.crossref.org
 */
public class DoiResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DoiResolver.class);
  private CloseableHttpClient http;
  private ObjectReader reader;

  public DoiResolver(CloseableHttpClient http) {
    this.http = http;
    reader = ApiModule.MAPPER.readerFor(CrossRefCitation.class);
  }

  public Citation resolve(DOI doi, IssueContainer issues) {
    HttpGet request = new HttpGet(doi.getUrl());
    request.addHeader(HttpHeaders.ACCEPT, MoreMediaTypes.APP_JSON_CSL);

    try (var resp = http.execute(request)) {
      if (resp.getCode() / 100 == 2) {
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
        LOG.warn("Failed to resolve DOI {}. HTTP {}", doi, resp.getCode());
        if (resp.getCode() == 404) {
          issues.addIssue(Issue.DOI_NOT_FOUND);
        } else {
          issues.addIssue(Issue.DOI_UNRESOLVED);
        }
      }

    } catch (IOException | ParseException e) {
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

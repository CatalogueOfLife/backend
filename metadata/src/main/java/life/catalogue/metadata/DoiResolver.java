package life.catalogue.metadata;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.JsonNodeType;

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
          issues.add(Issue.DOI_NOT_FOUND);
        } else {
          issues.add(Issue.DOI_UNRESOLVED);
        }
      }

    } catch (IOException | ParseException e) {
      // JacksonException are all covered with IOException
      LOG.error("Error resolving DOI {}", doi, e);
    }

    return null;
  }

  public static class CrossRefCitation extends Citation {
    private static ObjectReader ARRAY_READER = ApiModule.MAPPER.readerFor(
      new TypeReference<List<String>>() {}
    );

    private String getSingleValue(JsonNode node){
      if(node.getNodeType().equals(JsonNodeType.STRING)){
        return node.textValue();
      } else if(node.getNodeType().equals(JsonNodeType.ARRAY)){
        try{
          List<String> list = ARRAY_READER.readValue(node);
          if (list != null && !list.isEmpty()) {
            return list.get(0);
          }
        }catch (Exception ex){
        }
      }
      return null;
    }
    @JsonSetter("container-title")
    public void setContainerTitle(JsonNode node){
      //handle here as per your requirement
      setContainerTitle(getSingleValue(node));
    }
    @JsonSetter("collection-title")
    public void setCollectionTitle(JsonNode node){
      //handle here as per your requirement
      setCollectionTitle(getSingleValue(node));
    }

    @JsonSetter("title")
    public void setTitle(JsonNode node){
      setTitle(getSingleValue(node));
    }
    @JsonSetter("ISBN")
    public void setISBN(JsonNode node){
      setIsbn(getSingleValue(node));
    }

    @JsonSetter("ISSN")
    public void setISSN(JsonNode node){
      setIssn(getSingleValue(node));
    }
    public void setType(String type) {
      var ct = CSLTypeParser.PARSER.parseOrNull(type);
      super.setType(ct);
    }
  }
}

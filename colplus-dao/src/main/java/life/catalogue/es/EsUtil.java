package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.ddl.IndexDefinition;
import life.catalogue.es.mapping.Analyzer;
import life.catalogue.es.mapping.MappingsFactory;
import life.catalogue.es.mapping.MultiField;
import life.catalogue.es.model.NameUsageDocument;
import life.catalogue.es.name.NameUsageFieldLookup;
import life.catalogue.es.query.*;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class for interacting with Elasticsearch.
 * 
 */
public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Creates an index with the provided name and index configuration, with a document type mapping based on the provided model class.
   * 
   * @param client
   * @param modelClass
   * @param config
   * @throws IOException
   */
  public static void createIndex(RestClient client, Class<?> modelClass, IndexConfig config) throws IOException {
    IndexDefinition indexDef = IndexDefinition.loadDefaults();
    indexDef.setMappings(MappingsFactory.usingFields().getMapping(modelClass));
    indexDef.getSettings().getIndex().setNumberOfShards(config.numShards);
    indexDef.getSettings().getIndex().setNumberOfReplicas(config.numReplicas);
    LOG.trace("Creating index {}: {}", config.name, EsModule.writeDebug(indexDef));
    Request request = new Request("PUT", config.name);
    request.setJsonEntity(EsModule.write(indexDef));
    LOG.warn("Creating new ES Index {}", config.name);
    executeRequest(client, request);
  }

  /**
   * Creates an alias with the current timestamp appended to the base name of the index.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void createDefaultAlias(RestClient client, String index) throws IOException {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    String alias = index + "-" + dtf.format(Instant.now());
    createAlias(client, index, alias);
  }

  /**
   * Creates the provided alias for the provided index.
   * 
   * @param client
   * @param index
   * @param alias
   * @throws IOException
   */
  public static void createAlias(RestClient client, String index, String alias) throws IOException {
    Request request = new Request("PUT", index + "/_alias/" + alias);
    executeRequest(client, request);
  }

  /**
   * Returns all metadata about an index.
   * 
   * @param client
   * @param name
   * @return
   * @throws IOException
   */
  public static IndexDefinition getIndexDefinition(RestClient client, String name) throws IOException {
    try {
      Response response = client.performRequest(new Request("GET", name));
      return EsModule.readDDLObject(response.getEntity().getContent(), IndexDefinition.class);
    } catch (ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() == 404) { // That's OK
        throw new IllegalArgumentException("No such index: \"" + name + "\"");
      }
      throw new EsException(e);
    }
  }

  /**
   * Deletes the index with the provided name. Will silently do nothing if the index did not exist.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void deleteIndex(RestClient client, IndexConfig index) throws IOException {
    LOG.warn("Deleting ES Index {}", index.name);
    Response response = null;
    try {
      response = client.performRequest(new Request("DELETE", index.name));
    } catch (ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() == 404) { // That's OK
        return;
      }
    }
    if (response.getStatusLine().getStatusCode() >= 400) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
  }

  /**
   * Deletes the provided alias.
   * 
   * @param client
   * @param index
   * @param alias
   * @throws IOException
   */
  public static void deleteAlias(RestClient client, String index, String alias) throws IOException {
    Request request = new Request("DELETE", index + "/_alias/" + alias);
    executeRequest(client, request);
  }

  /**
   * Whether or not an index with the provided name exists.
   * 
   * @param client
   * @param index
   * @return
   * @throws IOException
   */
  public static boolean indexExists(RestClient client, String index) throws IOException {
    Response response = client.performRequest(new Request("HEAD", index));
    return response.getStatusLine().getStatusCode() == 200;
  }

  /**
   * Removes the dataset corresponding to the provided key. You must still refresh the index for the changes to become visible.
   * 
   * @param client
   * @param index
   * @param datasetKey
   * @return
   * @throws IOException
   */
  public static int deleteDataset(RestClient client, String index, int datasetKey) throws IOException {
    return deleteByQuery(client, index, new TermQuery("datasetKey", datasetKey));
  }

  /**
   * Removes the sector corresponding to the provided key. You must still refresh the index for the changes to become visible.
   * @throws IOException
   */
  public static int deleteSector(RestClient client, String index, int sectorKey) throws IOException {
    return deleteByQuery(client, index, new TermQuery("sectorKey", sectorKey));
  }

  /**
   * Deletes a taxonomic subtree from a single dataset.
   * It deletes all usage documents for a given datasetKey that share the given root taxonID in their classification.
   * @throws IOException
   */
  public static int deleteSubtree(RestClient client, String index, DSID<String> root) throws IOException {
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", root.getDatasetKey()))
        .filter(new TermQuery(NameUsageFieldLookup.INSTANCE.lookup(NameUsageSearchParameter.TAXON_ID), root.getId()));
    return deleteByQuery(client, index, query);
  }

  /**
   * Delete the documents corresponding to the provided dataset key and usage IDs. Returns the number of documents actually deleted. You
   * must still refresh the index for the changes to become visible.
   */
  public static int deleteNameUsages(RestClient client, String index, int datasetKey, Collection<String> usageIds) throws IOException {
    if (usageIds.isEmpty()) {
      return 0;
    }
    List<String> ids = (usageIds instanceof List) ? (List<String>) usageIds : new ArrayList<>(usageIds);
    int from = 0;
    int deleted = 0;
    while (from < ids.size()) {
      int to = Math.min(ids.size(), from + 1024); // 1024 is max num terms in terms query
      BoolQuery query = new BoolQuery()
          .filter(new TermQuery("datasetKey", datasetKey))
          .filter(new TermsQuery("usageId", ids.subList(from, to)));
      deleted += deleteByQuery(client, index, query);
      from = to;
    }
    return deleted;
  }

  /**
   * Deletes all documents from the index, but leaves the index itself intact. Very impractical for production code, but nice for testing
   * code.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void truncate(RestClient client, String index) throws IOException {
    deleteByQuery(client, index, new MatchAllQuery());
  }

  /**
   * Deletes all documents satisfying the provided query constraint(s). You must still refresh the index for the changes to become visible.
   * 
   * @param client
   * @param index
   * @param query
   * @return
   * @throws IOException
   */
  public static int deleteByQuery(RestClient client, String index, Query query) throws IOException {
    Request request = new Request("POST", index + "/_delete_by_query/?timeout=6h&conflicts=proceed");
    EsSearchRequest esRequest = EsSearchRequest.emptyRequest()
        .select()
        .where(query)
        .sortBy(SortField.DOC);
    esRequest.setQuery(query);
    request.setJsonEntity(esRequest.toString());
    Response response = executeRequest(client, request);
    return readFromResponse(response, "deleted");
  }

  /**
   * Makes all index documents become visible to clients.
   * 
   * @param client
   * @param name
   * @throws IOException
   */
  public static void refreshIndex(RestClient client, String name) throws IOException {
    Request request = new Request("POST", name + "/_refresh");
    executeRequest(client, request);
  }

  /**
   * Simple document count.
   * 
   * @param client
   * @param indexName
   * @return
   * @throws IOException
   */
  public static int count(RestClient client, String indexName) throws IOException {
    Request request = new Request("GET", indexName + "/_count");
    Response response = executeRequest(client, request);
    try {
      return (Integer) EsModule.readIntoMap(response.getEntity().getContent()).get("count");
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Inserts the provided object into the provided index and returns the generated document ID.
   * 
   * @param client
   * @param index
   * @param obj
   * @return
   * @throws IOException
   */
  public static String insert(RestClient client, String index, NameUsageDocument obj) throws IOException {
    Request request = new Request("POST", index + "/_doc");
    request.setJsonEntity(EsModule.write(obj));
    Response response = executeRequest(client, request);
    return readFromResponse(response, "_id");
  }

  /**
   * Returns the tokens that Elasticsearch would extract from the provided search phrase give the provided analyzer.
   * 
   * @param client
   * @param index
   * @param analyzer
   * @param searchPhrase
   * @return
   * @throws IOException
   */
  public static String[] getSearchTerms(RestClient client, String index, Analyzer analyzer, String searchPhrase) throws IOException {
    Request request = new Request("POST", index + "/_analyze");
    StringBuilder sb = new StringBuilder(100);
    MultiField mf = analyzer.getMultiField();
    String analyzerName = mf.getSearchAnalyzer();
    if (analyzerName == null) {
      analyzerName = mf.getAnalyzer();
    }
    sb.append("{\"analyzer\":\"")
        .append(analyzerName)
        .append("\",\"text\":\"")
        .append(searchPhrase)
        .append("\"}");
    request.setJsonEntity(sb.toString());
    Response response = executeRequest(client, request);
    @SuppressWarnings("rawtypes")
    List<HashMap> tokens = readFromResponse(response, "tokens");
    return tokens.stream().map(map -> map.get("token")).toArray(String[]::new);
  }

  /**
   * Executes the provided HTTP request and returns the HTTP response
   * 
   * @param client
   * @param request
   * @return
   * @throws IOException
   */
  public static Response executeRequest(RestClient client, Request request) throws IOException {
    Response response = client.performRequest(request);
    if (response.getStatusLine().getStatusCode() >= 400) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    return response;
  }

  /**
   * Whether or not Elasticsearch returned an OK response.
   * 
   * @param response
   * @return
   */
  public static boolean acknowlegded(Response response) {
    return readFromResponse(response, "acknowlegded");
  }

  @SuppressWarnings("unchecked")
  public static <T> T readFromResponse(Response response, String property) {
    try {
      return (T) EsModule.readIntoMap(response.getEntity().getContent()).get(property);
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

}

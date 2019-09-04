package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.ddl.DocumentTypeMapping;
import org.col.es.ddl.IndexDefinition;
import org.col.es.ddl.MappingFactory;
import org.col.es.ddl.SerializationUtil;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.dsl.Query;
import org.col.es.dsl.TermQuery;
import org.col.es.dsl.TermsQuery;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.ddl.SerializationUtil.pretty;
import static org.col.es.ddl.SerializationUtil.readIntoMap;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  private static int majorVersion;
  private static String versionString;

  public static int getMajorVersion(RestClient client) throws IOException {
    if (majorVersion == 0) {
      Request request = new Request("GET", "/");
      Response response = client.performRequest(request);
      HashMap<String, String> data = readFromResponse(response, "version");
      versionString = data.get("number");
      majorVersion = Integer.parseInt(versionString.substring(0, versionString.indexOf(".")));
    }
    return majorVersion;
  }

  public static String getVersionString(RestClient client) throws IOException {
    if (versionString == null) {
      Request request = new Request("GET", "/");
      Response response = client.performRequest(request);
      HashMap<String, String> data = readFromResponse(response, "version");
      versionString = data.get("number");
      majorVersion = Integer.parseInt(versionString.substring(0, versionString.indexOf(".")));
    }
    return versionString;
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
    Request request = new Request("HEAD", index);
    Response response = client.performRequest(request);
    return response.getStatusLine().getStatusCode() == 200;
  }

  @SuppressWarnings("unchecked")
  public static void createIdx(RestClient client, String index, IndexConfig cfg) throws IOException {
    if (getMajorVersion(client) == 7) {

    }
  }

  @SuppressWarnings("unchecked")
  public static <T> void createIndex(RestClient client, String index, IndexConfig cfg) throws IOException {

    // Load static config (analyzers, tokenizers, etc.) from es-settings.json
    Map<String, Object> settings = readIntoMap(loadSettings());
    // Insert configurable settings from config.yaml
    Map<String, Object> indexSettings = (Map<String, Object>) settings.get("index");
    indexSettings.put("number_of_shards", cfg.numShards);
    indexSettings.put("number_of_replicas", cfg.numReplicas);

    // Create document type mapping
    Map<String, Object> mappings = new HashMap<>();
    MappingFactory<T> factory = new MappingFactory<>();
    factory.setMapEnumToInt(true);
    DocumentTypeMapping mapping = factory.getMapping(cfg.modelClass);
    mappings.put(EsConfig.DEFAULT_TYPE_NAME, mapping);

    // Combine into full request
    Map<String, Object> indexSpec = new HashMap<>();
    indexSpec.put("settings", settings);
    indexSpec.put("mappings", mappings);

    if (LOG.isTraceEnabled()) {
      LOG.trace("Creating index {}: {}", index, pretty(indexSpec));
    }

    Request request = new Request("PUT", index);
    request.setJsonEntity(SerializationUtil.serialize(indexSpec));
    executeRequest(client, request);
  }

  @SuppressWarnings("unchecked")
  public static <T> void createIndex7(RestClient client, String index, IndexConfig cfg) throws IOException {

    // Load static config (analyzers, tokenizers, etc.) from es-settings.json
    Map<String, Object> settings = readIntoMap(loadSettings());
    // Insert configurable settings from config.yaml
    Map<String, Object> indexSettings = (Map<String, Object>) settings.get("index");
    indexSettings.put("number_of_shards", cfg.numShards);
    indexSettings.put("number_of_replicas", cfg.numReplicas);

    // Create document type mapping
    MappingFactory<T> factory = new MappingFactory<>();
    factory.setMapEnumToInt(true);

    // Combine into full request
    Map<String, Object> indexSpec = new HashMap<>();
    indexSpec.put("settings", settings);
    indexSpec.put("mappings", factory.getMapping(cfg.modelClass));

    if (LOG.isTraceEnabled()) {
      LOG.trace("Creating index {}: {}", index, pretty(indexSpec));
    }

    Request request = new Request("PUT", index);
    request.setJsonEntity(SerializationUtil.serialize(indexSpec));
    executeRequest(client, request);
  }

  /**
   * Deletes the provided index. Will silently do nothing if the index did not exist.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void deleteIndex(RestClient client, String index) throws IOException {
    Request request = new Request("DELETE", index);
    Response response = null;
    try {
      response = client.performRequest(request);
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
   * Removes the dataset corresponding to the provided key. You must still refresh the index for the changes to become
   * visible.
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
   * Removes the sector corresponding to the provided key. You must still refresh the index for the changes to become
   * visible.
   * 
   * @param client
   * @param index
   * @param sectorKey
   * @return
   * @throws IOException
   */
  public static int deleteSector(RestClient client, String index, int sectorKey) throws IOException {
    return deleteByQuery(client, index, new TermQuery("sectorKey", sectorKey));
  }

  /**
   * Delete the documents corresponding to the provided dataset key and usage IDs. Returns the number of documents
   * actually deleted. You must still refresh the index for the changes to become visible.
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
   * Deletes all documents satisfying the provided query constraint(s). You must still refresh the index for the changes
   * to become visible.
   * 
   * @param client
   * @param index
   * @param query
   * @return
   * @throws IOException
   */
  public static int deleteByQuery(RestClient client, String index, Query query) throws IOException {
    Request request = new Request("POST", index + "/_delete_by_query");
    EsSearchRequest esRequest = new EsSearchRequest();
    esRequest.setQuery(query);
    request.setJsonEntity(esRequest.toString());
    Response response = executeRequest(client, request);
    return readFromResponse(response, "total");
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
    Request request = new Request("GET", indexName + "/" + EsConfig.DEFAULT_TYPE_NAME + "/_count");
    Response response = executeRequest(client, request);
    try {
      return (Integer) readIntoMap(response.getEntity().getContent()).get("count");
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Inserts the provided object into the provided index and returns the generated document ID.
   * 
   * @param <T>
   * @param client
   * @param index
   * @param obj
   * @return
   * @throws IOException
   */
  public static <T> String insert(RestClient client, String index, T obj) throws IOException {
    Request request = new Request("POST", index + "/_doc");
    request.setJsonEntity(serialize(obj));
    Response response = executeRequest(client, request);
    return readFromResponse(response, "_id");
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

  private static InputStream loadSettings() {
    return EsUtil7.class.getResourceAsStream("es-settings.json");
  }

  @SuppressWarnings("unchecked")
  private static <T> T readFromResponse(Response response, String property) {
    try {
      return (T) readIntoMap(response.getEntity().getContent()).get(property);
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  // Used for indexing documents. Use SerializationUtil for creating indexes and type mappings.
  private static String serialize(Object obj) {
    try {
      return EsModule.MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}

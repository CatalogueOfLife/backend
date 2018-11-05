package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.col.api.model.NameUsage;
import org.col.api.search.NameUsageWrapper;
import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.col.es.mapping.SerializationUtil;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.mapping.SerializationUtil.pretty;
import static org.col.es.mapping.SerializationUtil.readIntoMap;

public class EsUtil {

  public static TypeReference<NameUsageWrapper<? extends NameUsage>> NUW_TYPE_REF =
      new TypeReference<NameUsageWrapper<? extends NameUsage>>() {};

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  @SuppressWarnings("unchecked")
  public static <T> void createIndex(RestClient client, String indexName, IndexConfig cfg) {

    LOG.info("Creating index {}", indexName);

    // Load global / static config (analyzers, tokenizers, etc.)
    Map<String, Object> settings = readIntoMap(loadSettings());
    // Insert configurable / index-specific settings
    Map<String, Object> indexSettings = (Map<String, Object>) settings.get("index");
    indexSettings.put("number_of_shards", cfg.numShards);
    indexSettings.put("number_of_replicas", cfg.numReplicas);

    // Create document type mapping
    Map<String, Object> mappings = new HashMap<>();
    MappingFactory<T> factory = new MappingFactory<>();
    factory.setMapEnumToInt(cfg.storeEnumAsInt);
    Mapping<T> mapping = factory.getMapping(cfg.modelClass);
    mappings.put(EsConfig.DEFAULT_TYPE_NAME, mapping);

    // Combine into full request
    Map<String, Object> indexSpec = new HashMap<>();
    indexSpec.put("settings", settings);
    indexSpec.put("mappings", mappings);
    LOG.debug(pretty(indexSpec));

    Request request = new Request("PUT", indexName);
    request.setJsonEntity(SerializationUtil.serialize(indexSpec));
    executeRequest(client, request);
  }

  public static void deleteIndex(RestClient client, String indexName) {
    LOG.info("Deleting index {}", indexName);
    Request request = new Request("DELETE", indexName);
    Response response;
    try {
      response = client.performRequest(request);
    } catch (ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() == 404) { // That's OK
        LOG.info("No such index: {} (nothing deleted)", indexName);
        return;
      }
      throw new EsException(e);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
  }

  public static void refreshIndex(RestClient client, String name) {
    LOG.info("Refreshing index {}", name);
    Request request = new Request("POST", name + "/_refresh");
    executeRequest(client, request);
  }

  /**
   * Simple document count.
   * 
   * @param client
   * @param indexName
   * @return
   */
  public static int count(RestClient client, String indexName) {
    LOG.info("Counting index {}", indexName);
    Request request = new Request("GET", indexName + "/_doc/_count");
    Response response = executeRequest(client, request);
    try {
      return (Integer) readIntoMap(response.getEntity().getContent()).get("count");
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  public static <T> void insert(RestClient client, String indexName, IndexConfig cfg, T obj) {
    LOG.info("Inserting {} into index {}", obj.getClass().getSimpleName(), indexName);
    String url = indexName + "/" + EsConfig.DEFAULT_TYPE_NAME;
    Request request = new Request("POST", url);
    request.setJsonEntity(serialize(obj, cfg));
    executeRequest(client, request);
  }

  public static Response executeRequest(RestClient client, Request request) throws EsException {
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() >= 400) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    return response;
  }

  private static InputStream loadSettings() {
    return EsUtil.class.getResourceAsStream("es-settings.json");
  }

  private static String serialize(Object obj, IndexConfig cfg) {
    try {
      return cfg.getMapper().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}

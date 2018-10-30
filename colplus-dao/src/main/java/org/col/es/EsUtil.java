package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.mapping.SerializationUtil.pretty;
import static org.col.es.mapping.SerializationUtil.readIntoMap;
import static org.col.es.mapping.SerializationUtil.serialize;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  @SuppressWarnings("unchecked")
  public static <T> void createIndex(RestClient client, String name, IndexConfig cfg)
      throws EsException {

     LOG.info("Creating index {}", name);

    // Load global / static config (analyzers, tokenizers, etc.)
    Map<String, Object> settings = readIntoMap(loadSettings());
    // Insert index-specific settings
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

    Request request = new Request("PUT", name);
    request.setJsonEntity(serialize(indexSpec));
    executeRequest(client, request);
  }

  public static void deleteIndex(RestClient client, String name) throws EsException {
    LOG.info("Deleting index {}", name);
    Request request = new Request("DELETE", name);
    Response response;
    try {
      response = client.performRequest(request);
    } catch (ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() == 404) { // That's OK
        LOG.info("No such index: {} (nothing deleted)", name);
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

  public static void refreshIndex(RestClient client, String name) throws EsException {
    LOG.info("Refreshing index {}", name);
    Request request = new Request("POST", name + "/_refresh");
    executeRequest(client, request);
  }

  /**
   * Simple document count.
   * 
   * @param client
   * @param name
   * @return
   * @throws EsException
   */
  public static int count(RestClient client, String name) throws EsException {
    LOG.info("Counting index {}", name);
    Request request = new Request("GET", name + "/_doc/_count");
    Response response = executeRequest(client, request);
    try {
      return (Integer) readIntoMap(response.getEntity().getContent()).get("count");
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  public static Response executeRequest(RestClient client, Request request) throws EsException {
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    return response;
  }

  private static InputStream loadSettings() {
    return EsUtil.class.getResourceAsStream("es-settings.json");
  }

}

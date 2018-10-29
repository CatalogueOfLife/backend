package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.col.es.mapping.MappingSerializer;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsUtil {
  
  //public static final ObjectWriter WRITER = ApiModule.MAPPER.writerFor(EsNameUsage.class);

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  @SuppressWarnings("unchecked")
  public static <T> void createIndex(RestClient client, String name, IndexConfig cfg)
      throws EsException {
    LOG.info("Creating index {}", name);
    Map<String, Object> indexSpec = new HashMap<>();
    // First load static configuration (ngram definitions etc.)
    InputStream is = EsUtil.class.getResourceAsStream("es-settings.json");
    ObjectMapper om = new ObjectMapper();
    TypeReference<Map<String, Object>> tr = new TypeReference<Map<String, Object>>() {};
    Map<String, Object> settings;
    try {
      settings = om.readValue(is, tr);
    } catch (IOException e) {
      throw new AssertionError("Elasticsearch configuration error", e);
    }
    // Add index-specific settings
    Map<String, Object> indexSettings = (Map<String, Object>) settings.get("index");
    indexSettings.put("number_of_shards", cfg.numShards);
    indexSettings.put("number_of_replicas", cfg.numReplicas);
    indexSpec.put("settings", settings);
    // Now add type mapping
    Class<T> c;
    try {
      c = (Class<T>) Class.forName(cfg.modelClass);
    } catch (ClassNotFoundException e) {
      throw new EsException("Configuration error. No such model class: " + cfg.modelClass, e);
    }
    Map<String, Object> mappings = new HashMap<>();
    indexSpec.put("mappings", mappings);
    Mapping<T> mapping = new MappingFactory<T>().getMapping(c);
    mappings.put(EsConfig.DEFAULT_TYPE_NAME, new MappingSerializer<>(mapping).asMap());
    // LOG.debug(serialize(indexSpec));
    Request request = new Request("PUT", name);
    request.setJsonEntity(serialize(indexSpec));
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
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
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
  }

  public static int count(RestClient client, String name) throws EsException {
    LOG.info("Counting index {}", name);
    Request request = new Request("GET", name + "/_doc/_count");
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new EsException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    TypeReference<Map<String, Object>> tr = new TypeReference<Map<String, Object>>() {};
    ObjectMapper om = new ObjectMapper();
    Map<String, Object> map;
    try {
      map = om.readValue(response.getEntity().getContent(), tr);
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
    return (Integer) map.get("count");
  }
  
  //private static readIntoMap

  private static String serialize(Map<String, Object> map) {
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(Include.NON_NULL);
    om.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    } catch (JsonProcessingException e) {
      throw new AssertionError("Serialization failure", e);
    }
  }

}

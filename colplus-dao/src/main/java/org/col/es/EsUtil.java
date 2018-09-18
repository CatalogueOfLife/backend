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
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsUtil {

  /**
   * The default name of the type created within an index. Multiple types per index are deprecated
   * in ES 6.x, so the name of the type created within an index can basically be arbitrary.
   */
  public static final String DEFAULT_TYPE_NAME = "doc";

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);
  private static final String MODEL_PACKAGE = "org.col.api.model";

  /**
   * Creates all indices for the specified model class. In priniple a model class may be persisted
   * to multiple indices (e.g. in case sharding/partitioning seems like a good idea).
   *
   * @param cfg
   * @param modelClass
   * @throws EsException
   */
  public static void createIndex(EsConfig cfg, Class<?> modelClass) throws EsException {
    try (RestClient client = new EsClientFactory(cfg).createClient()) {
      for (IndexConfig idxCfg : cfg.indices) {
        String s;
        if (idxCfg.modelClass.contains(".")) {
          s = idxCfg.modelClass;
        } else {
          s = MODEL_PACKAGE + "." + idxCfg.modelClass;
        }
        if (s.equals(modelClass.getName())) {
          createIndex(client, idxCfg);
          return;
        }
      }
    } catch (IOException e) {
      throw new EsException(e);
    }
    throw new EsException("Missing configuration for class " + modelClass.getName());
  }

  @SuppressWarnings("unchecked")
  public static <T> void createIndex(RestClient client, IndexConfig cfg) throws EsException {
    LOG.info("Creating index {}", cfg.name);
    Map<String, Object> indexSpec = new HashMap<>();
    // First load index-independent configuration (ngram definitions etc.)
    String resource = "es-settings.json";
    InputStream is = EsUtil.class.getResourceAsStream(resource);
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
      if (cfg.modelClass.contains(".")) {
        c = (Class<T>) Class.forName(cfg.modelClass);
      } else {
        c = (Class<T>) Class.forName(MODEL_PACKAGE + "." + cfg.modelClass);
      }
    } catch (ClassNotFoundException e) {
      throw new EsException("Configuration error. No such model class: " + cfg.modelClass, e);
    }
    Map<String, Object> mappings = new HashMap<>();
    indexSpec.put("mappings", mappings);
    Mapping<T> mapping = new MappingFactory<T>().getMapping(c);
    mappings.put(DEFAULT_TYPE_NAME, new MappingSerializer<>(mapping).asMap());
    if (LOG.isDebugEnabled()) {
      LOG.debug(serialize(indexSpec));
    }
    Request request = new Request("PUT", cfg.name);
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

  /**
   * Deletes all indices associated with the specified model class.
   *
   * @param cfg
   * @param modelClass
   * @throws EsException 
   */
  public static void deleteIndex(EsConfig cfg, Class<?> modelClass) throws EsException {
    try (RestClient client = new EsClientFactory(cfg).createClient()) {
      for (IndexConfig idxCfg : cfg.indices) {
        String s;
        if (idxCfg.modelClass.contains(".")) {
          s = idxCfg.modelClass;
        } else {
          s = MODEL_PACKAGE + "." + idxCfg.modelClass;
        }
        if (s.equals(modelClass.getName())) {
          deleteIndex(client, idxCfg);
          return;
        }
      }
    } catch (IOException e) {
      throw new EsException(e);
    }
    throw new EsException("Missing configuration for class " + modelClass.getName());
  }

  public static void deleteIndex(EsConfig cfg, String indexName) throws EsException {
    try (RestClient client = new EsClientFactory(cfg).createClient()) {
      for (IndexConfig idxCfg : cfg.indices) {
        if (idxCfg.name.equals(indexName)) {
          deleteIndex(client, idxCfg);
          return;
        }
      }
    } catch (IOException e) {
      throw new EsException(e);
    }
    throw new EsException("Missing configuration for index " + indexName);
  }

  public static void deleteIndex(RestClient client, IndexConfig cfg) throws EsException {
    LOG.info("Deleting index {}", cfg.name);
    Request request = new Request("DELETE", cfg.name);
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

}

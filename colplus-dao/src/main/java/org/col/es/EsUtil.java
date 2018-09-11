package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.col.es.mapping.MappingSerializer;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.index.IndexNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);
  private static final String MODEL_PACKAGE = "org.col.api.model";

  /**
   * Creates all indices for the specified model class. In priniple a model class may be persisted
   * to multiple indices (e.g. in case sharding/partitioning seems like a good idea).
   * 
   * @param cfg
   * @param modelClass
   */
  public static void createIndices(EsConfig cfg, Class<?> modelClass) {
    try (Client client = new EsClientFactory(cfg).getEsClient()) {
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
    }
    throw new EsException("Missing configuration for class " + modelClass.getName());
  }

  public static void createIndex(EsConfig cfg, String indexName) {
    try (Client client = new EsClientFactory(cfg).getEsClient()) {
      for (IndexConfig idxCfg : cfg.indices) {
        if (idxCfg.name.equals(indexName)) {
          createIndex(client, idxCfg);
          return;
        }
      }
    }
    throw new EsException("Missing configuration for index " + indexName);
  }

  public static void createIndex(Client client, IndexConfig cfg) {
    LOG.info("Creating index {}", cfg.name);
    // First load static configuration
    String resource = "es-settings.json";
    InputStream is = EsUtil.class.getResourceAsStream(resource);
    Builder builder = Settings.builder();
    try {
      builder.loadFromStream(resource, is);
    } catch (IOException e) {
      throw new EsException(e);
    }
    // Then add configurable settings
    builder.put("index.number_of_shards", cfg.numShards);
    builder.put("index.number_of_replicas", cfg.numReplicas);
    CreateIndexRequestBuilder request = client.admin().indices().prepareCreate(cfg.name);
    request.setSettings(builder.build());
    CreateIndexResponse response = request.execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new EsException("Failed to create index " + cfg.name);
    }
    createType(client, cfg);
  }

  /**
   * Creates index and its associated type as specified though the IndexConfig instance.
   */
  @SuppressWarnings("unchecked")
  private static <T> void createType(Client client, IndexConfig cfg) {
    String index = cfg.name;
    String type = cfg.name;
    LOG.info("Creating type {}", type);
    PutMappingRequestBuilder request = client.admin().indices().preparePutMapping(index);
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
    Mapping<T> mapping = new MappingFactory<T>().getMapping(c);
    if (LOG.isDebugEnabled()) {
      LOG.debug(new MappingSerializer<>(mapping, true).serialize());
    }
    request.setSource(new MappingSerializer<>(mapping).asMap());
    request.setType(type);
    PutMappingResponse response = request.execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new EsException("Failed to create type " + type);
    }
  }

  /**
   * Deletes all indices associated with the specified model class.
   * 
   * @param cfg
   * @param modelClass
   */
  public static void deleteIndices(EsConfig cfg, Class<?> modelClass) {
    try (Client client = new EsClientFactory(cfg).getEsClient()) {
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
    }
    throw new EsException("Missing configuration for class " + modelClass.getName());
  }

  public static void deleteIndex(EsConfig cfg, String indexName) {
    try (Client client = new EsClientFactory(cfg).getEsClient()) {
      for (IndexConfig idxCfg : cfg.indices) {
        if (idxCfg.name.equals(indexName)) {
          deleteIndex(client, idxCfg);
          return;
        }
      }
    }
    throw new EsException("Missing configuration for index " + indexName);
  }

  public static void deleteIndex(Client client, IndexConfig cfg) {
    LOG.info("Deleting index {}", cfg.name);
    DeleteIndexRequestBuilder request = client.admin().indices().prepareDelete(cfg.name);
    try {
      DeleteIndexResponse response = request.execute().actionGet();
      if (!response.isAcknowledged()) {
        throw new RuntimeException("Failed to delete index " + cfg.name);
      }
      LOG.info("Index deleted");
    } catch (IndexNotFoundException e) {
      LOG.info("No such index \"{}\" (nothing deleted)", cfg.name);
    }

  }

}

package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);
  private static final String MODEL_PACKAGE = "org.col.api.model";

  public static void createIndex(EsConfig cfg, String indexName) {
    for (IndexConfig idxCfg : cfg.indices) {
      if (idxCfg.name.equals(indexName)) {
        try (Client client = new EsClientFactory(cfg).getEsClient()) {
          createType(client, idxCfg);
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
      c = (Class<T>) Class.forName(MODEL_PACKAGE + "." + cfg.modelClass);
    } catch (ClassNotFoundException e) {
      throw new EsException("Configuration error. No such model class: " + cfg.modelClass, e);
    }
    Mapping<T> mapping = new MappingFactory<T>().getMapping(c);
    request.setSource(mapping);
    request.setType(type);
    PutMappingResponse response = request.execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new EsException("Failed to create type " + type);
    }
  }

}

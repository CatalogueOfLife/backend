package org.col.es;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.col.es.mapping.Mapping;
import org.col.es.mapping.MappingFactory;
import org.col.es.mapping.SerializationUtil;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.mapping.SerializationUtil.pretty;
import static org.col.es.mapping.SerializationUtil.readIntoMap;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Whether or not the index with the provided name exists.
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
  public static <T> void createIndex(RestClient client, String index, IndexConfig cfg) throws IOException {

    LOG.info("Creating index {}", index);

    // Load global / static config (analyzers, tokenizers, etc.)
    Map<String, Object> settings = readIntoMap(loadSettings());
    // Insert configurable / index-specific settings
    Map<String, Object> indexSettings = (Map<String, Object>) settings.get("index");
    indexSettings.put("number_of_shards", cfg.numShards);
    indexSettings.put("number_of_replicas", cfg.numReplicas);

    // Create document type mapping
    Map<String, Object> mappings = new HashMap<>();
    MappingFactory<T> factory = new MappingFactory<>();
    factory.setMapEnumToInt(true);
    Mapping<T> mapping = factory.getMapping(cfg.modelClass);
    mappings.put(EsConfig.DEFAULT_TYPE_NAME, mapping);

    // Combine into full request
    Map<String, Object> indexSpec = new HashMap<>();
    indexSpec.put("settings", settings);
    indexSpec.put("mappings", mappings);

    if (LOG.isTraceEnabled()) {
      LOG.trace(pretty(indexSpec));
    }

    Request request = new Request("PUT", index);
    request.setJsonEntity(SerializationUtil.serialize(indexSpec));
    executeRequest(client, request);
  }

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
   * Deletes all documents satisfying the provided query constraints. You must still refresh the index for the changes to become visible.
   * 
   * @param client
   * @param index
   * @param query
   * @return
   * @throws IOException
   */
  public static int deleteByQuery(RestClient client, String index, Query query) throws IOException {
    String url = String.format("%s/%s/_delete_by_query", index, EsConfig.DEFAULT_TYPE_NAME);
    Request request = new Request("POST", url);
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(query);
    request.setJsonEntity(EsModule.QUERY_WRITER.writeValueAsString(esr));
    Response response = client.performRequest(request);
    if (response.getStatusLine().getStatusCode() >= 400) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    Map<String, Object> feedback =
        EsModule.MAPPER.readValue(response.getEntity().getContent(), new TypeReference<Map<String, Object>>() {});
    Integer total = (Integer) feedback.get("total");
    return total.intValue();
  }

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

  public static <T> void insert(RestClient client, String index, Collection<T> objs) throws IOException {
    for (T obj : objs) {
      insert(client, index, obj);
    }
  }

  public static <T> void insert(RestClient client, String index, T obj) throws IOException {
    String url = index + "/" + EsConfig.DEFAULT_TYPE_NAME;
    Request request = new Request("POST", url);
    request.setJsonEntity(serialize(obj));
    executeRequest(client, request);
  }

  public static Response executeRequest(RestClient client, Request request) throws IOException {
    Response response = client.performRequest(request);
    if (response.getStatusLine().getStatusCode() >= 400) {
      throw new EsException(response.getStatusLine().getReasonPhrase());
    }
    return response;
  }

  private static InputStream loadSettings() {
    return EsUtil.class.getResourceAsStream("es-settings.json");
  }

  private static String serialize(Object obj) {
    try {
      return EsModule.MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}

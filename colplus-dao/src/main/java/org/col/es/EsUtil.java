package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.ddl.Index6Definition;
import org.col.es.ddl.Index7Definition;
import org.col.es.ddl.IndexDefinition;
import org.col.es.ddl.JsonUtil;
import org.col.es.ddl.Settings;
import org.col.es.mapping.Mappings;
import org.col.es.mapping.MappingsFactory;
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

import static org.col.es.ddl.JsonUtil.pretty;
import static org.col.es.ddl.JsonUtil.readIntoMap;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Creates an index with the provided name and index configuration, with a document type mapping based on the provided
   * model class.
   * 
   * @param client
   * @param name
   * @param modelClass
   * @param config
   * @throws IOException
   */
  public static void createIndex(RestClient client, String name, Class<?> modelClass, IndexConfig config) throws IOException {
    Mappings mappings = new MappingsFactory().getMapping(modelClass);
    Settings settings = Settings.getDefaultSettings();
    settings.getIndex().setNumberOfShards(config.numShards);
    settings.getIndex().setNumberOfReplicas(config.numReplicas);
    IndexDefinition<?> indexDef;
    if (EsServerVersion.getInstance(client).is(7)) {
      indexDef = new Index7Definition(settings, mappings);
    } else {
      indexDef = new Index6Definition(settings, mappings);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace("Creating index {}: {}", name, pretty(indexDef));
    }
    Request request = new Request("PUT", name);
    request.setJsonEntity(JsonUtil.serialize(indexDef));
    executeRequest(client, request);
  }

  /**
   * Deletes the index with the provided name. Will silently do nothing if the index did not exist.
   * 
   * @param client
   * @param name
   * @throws IOException
   */
  public static void deleteIndex(RestClient client, String name) throws IOException {
    Request request = new Request("DELETE", name);
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
    Request request = new Request("GET", indexName + "/_count");
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

  @SuppressWarnings("unchecked")
  public static <T> T readFromResponse(Response response, String property) {
    try {
      return (T) readIntoMap(response.getEntity().getContent()).get(property);
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  // Used for indexing/retrieving **documents**. Use JsonUtil when creating the indexes themselves!
  private static String serialize(Object obj) {
    try {
      return EsModule.MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}

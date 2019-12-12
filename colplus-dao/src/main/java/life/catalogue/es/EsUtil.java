package life.catalogue.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.es.ddl.Index6Definition;
import life.catalogue.es.ddl.Index7Definition;
import life.catalogue.es.ddl.IndexDefinition;
import life.catalogue.es.ddl.Settings;
import life.catalogue.es.mapping.Mappings;
import life.catalogue.es.mapping.MappingsFactory;
import life.catalogue.es.model.NameUsageDocument;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.MatchAllQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SortField;
import life.catalogue.es.query.TermQuery;
import life.catalogue.es.query.TermsQuery;

public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Creates an index with the provided name and index configuration, with a document type mapping based on the provided model class.
   * 
   * @param client
   * @param name
   * @param modelClass
   * @param config
   * @throws IOException
   */
  public static void createIndex(RestClient client, String name, Class<?> modelClass, IndexConfig config) throws IOException {
    MappingsFactory factory = MappingsFactory.usingFields();
    Mappings mappings = factory.getMapping(modelClass);
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
      LOG.trace("Creating index {}: {}", name, EsModule.writeDebug(indexDef));
    }
    Request request = new Request("PUT", name);
    request.setJsonEntity(EsModule.write(indexDef));
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

  public static Set<String> listAliases(RestClient client, String index) throws IOException {
    if (!indexExists(client, index)) {
      throw new IllegalArgumentException("No such index: " + index);
    }
    Response response = executeRequest(client, new Request("GET", index));
    return null;
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
    Request request = new Request("POST", index + "/_delete_by_query/?timeout=12h");
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
      return (T) EsModule.readIntoMap(response.getEntity().getContent()).get(property);
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

}

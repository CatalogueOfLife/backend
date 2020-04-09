package life.catalogue.es;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;
import life.catalogue.api.model.DSID;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.ddl.IndexDefinition;
import life.catalogue.es.ddl.MappingsFactory;
import life.catalogue.es.ddl.MultiField;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.MatchAllQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SortField;
import life.catalogue.es.query.TermQuery;
import life.catalogue.es.query.TermsQuery;
import static life.catalogue.common.text.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Utility class for interacting with Elasticsearch.
 * 
 */
public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Creates an index with the provided name and index configuration, with a document type mapping based on the provided model class.
   * 
   * @param client
   * @param modelClass
   * @param config
   * @throws IOException
   */
  public static void createIndex(RestClient client, Class<?> modelClass, IndexConfig config) throws IOException {
    IndexDefinition indexDef = IndexDefinition.loadDefaults();
    indexDef.setMappings(MappingsFactory.usingFields().getMapping(modelClass));
    indexDef.getSettings().getIndex().setNumberOfShards(config.numShards);
    indexDef.getSettings().getIndex().setNumberOfReplicas(config.numReplicas);
    LOG.trace("Creating index {}: {}", config.name, EsModule.writeDebug(indexDef));
    Request request = new Request("PUT", config.name);
    request.setJsonEntity(EsModule.write(indexDef));
    LOG.warn("Creating new ES Index {}", config.name);
    executeRequest(client, request);
  }

  /**
   * Creates an alias with the current timestamp appended to the base name of the index.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void createDefaultAlias(RestClient client, String index) {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    String alias = index + "-" + dtf.format(Instant.now());
    createAlias(client, index, alias);
  }

  /**
   * Creates the provided alias for the provided index.
   * 
   * @param client
   * @param index
   * @param alias
   * @throws IOException
   */
  public static void createAlias(RestClient client, String index, String alias) {
    Request request = new Request("PUT", index + "/_alias/" + alias);
    executeRequest(client, request);
  }

  /**
   * Returns all metadata about an index.
   * 
   * @param client
   * @param name
   * @return
   * @throws IOException
   */
  public static IndexDefinition getIndexDefinition(RestClient client, String name) throws IOException {
    try {
      Response response = client.performRequest(new Request("GET", name));
      return EsModule.readDDLObject(response.getEntity().getContent(), IndexDefinition.class);
    } catch (ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() == 404) {
        throw new IllegalArgumentException("No such index: \"" + name + "\"");
      }
      throw new EsException(e);
    }
  }

  /**
   * Deletes the index with the provided name. Will silently do nothing if the index did not exist.
   * 
   * @param client
   * @param index
   * @throws IOException
   */
  public static void deleteIndex(RestClient client, IndexConfig index) throws IOException {
    LOG.warn("Deleting ES Index {}", index.name);
    Response response = null;
    try {
      response = client.performRequest(new Request("DELETE", index.name));
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
   * Deletes the provided alias.
   * 
   * @param client
   * @param index
   * @param alias
   * @throws IOException
   */
  public static void deleteAlias(RestClient client, String index, String alias) throws IOException {
    Request request = new Request("DELETE", index + "/_alias/" + alias);
    executeRequest(client, request);
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
    Response response = client.performRequest(new Request("HEAD", index));
    return response.getStatusLine().getStatusCode() == 200;
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
  public static int deleteDataset(RestClient client, String index, int datasetKey) {
    return deleteByQuery(client, index, new TermQuery("datasetKey", datasetKey));
  }

  /**
   * Removes the sector corresponding to the provided key. You must still refresh the index for the changes to become visible.
   * 
   * @throws IOException
   */
  public static int deleteSector(RestClient client, String index, int sectorKey) {
    return deleteByQuery(client, index, new TermQuery("sectorKey", sectorKey));
  }

  /**
   * Deletes a taxonomic subtree from a single dataset. It deletes all usage documents for a given datasetKey that share the given root
   * taxonID in their classification.
   * 
   * @throws IOException
   */
  public static int deleteSubtree(RestClient client, String index, DSID<String> root) {
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", root.getDatasetKey()))
        .filter(new TermQuery(NameUsageFieldLookup.INSTANCE.lookup(NameUsageSearchParameter.TAXON_ID), root.getId()));
    return deleteByQuery(client, index, query);
  }

  /**
   * Delete the documents corresponding to the provided dataset key and usage IDs. Returns the number of documents actually deleted. You
   * must still refresh the index for the changes to become visible.
   */
  public static int deleteNameUsages(RestClient client, String index, int datasetKey, Collection<String> usageIds) {
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
  public static void truncate(RestClient client, String index) {
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
  @SuppressWarnings("unchecked")
  public static int deleteByQuery(RestClient client, String index, Query query) {
    int attempts = 20;
    Request request = new Request("POST", index + "/_delete_by_query/?wait_for_completion=false&conflicts=proceed");
    EsSearchRequest esRequest = EsSearchRequest.emptyRequest()
        .select()
        .where(query)
        .sortBy(SortField.DOC);
    esRequest.setQuery(query);
    request.setJsonEntity(esRequest.toString());
    Response response;
    while (true) {
      try {
        // Execute and return immediately (wait_for_completion=false)
        // Response will contain a task ID rather than the result
        response = executeRequest(client, request);
        break;
      } catch (TooManyRequestsException e) {
        sleep(1000 * 60 * 5);
      }
    }
    String taskId = readFromResponse(response, "task");
    request = new Request("GET", "_tasks/" + taskId);
    for (int i = 0; i < attempts; i++) {
      // After every attempt we double the wait time (starting with 32 msecs)
      sleep(32 << i);
      response = executeRequest(client, request);
      Map<String, Object> content = readResponse(response);
      if ((Boolean) content.get("completed")) {
        executeAndForget(client, new Request("DELETE", ".tasks/_doc/" + taskId));
        content = (Map<String, Object>) content.get("response");
        List<?> failures = (List<?>) content.get("failures");
        if (failures == null || failures.isEmpty()) {
          return (Integer) content.get("deleted");
        }
        throw new EsRequestException("Error executing delete_by_query request. Failures: %s. Query: %s",
            EsModule.writeDebug(failures),
            EsModule.writeDebug(query));
      }
    }
    throw new EsRequestException("delete_by_query request failed to complete");
  }

  /**
   * Makes all index documents become visible to clients. This is a blocking call because it is assumed that if you call this method, you
   * really need the result of your inserts/updates/deletes to become visible before you can proceed.
   * 
   * @param client
   * @param name
   * @throws IOException
   */
  public static void refreshIndex(RestClient client, String name) {
    executeWithRetry(client, new Request("POST", name + "/_refresh"));
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
    return readFromResponse(response, "count");
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
  public static String insert(RestClient client, String index, EsNameUsage obj) throws IOException {
    Request request = new Request("POST", index + "/_doc");
    request.setJsonEntity(EsModule.write(obj));
    Response response = executeRequest(client, request);
    return readFromResponse(response, "_id");
  }

  /**
   * Returns the tokens that Elasticsearch would extract from the provided search phrase give the provided analyzer.
   * 
   * @param client
   * @param index
   * @param analyzer
   * @param searchPhrase
   * @return
   * @throws IOException
   */
  public static String[] getSearchTerms(RestClient client, String index, Analyzer analyzer, String searchPhrase) throws IOException {
    if (StringUtils.isBlank(searchPhrase)) {
      return EMPTY_STRING_ARRAY;
    }
    Request request = new Request("POST", index + "/_analyze");
    StringBuilder sb = new StringBuilder(100);
    MultiField mf = analyzer.getMultiField();
    String analyzerName = mf.getSearchAnalyzer();
    if (analyzerName == null) {
      analyzerName = mf.getAnalyzer();
    }
    sb.append("{\"analyzer\":")
        .append(EsModule.escape(analyzerName))
        .append(",\"text\":")
        .append(EsModule.escape(searchPhrase))
        .append("}");
    request.setJsonEntity(sb.toString());
    Response response = executeRequest(client, request);
    List<Map<String, Object>> tokens = readFromResponse(response, "tokens");
    if (tokens == null || tokens.isEmpty()) {
      return EMPTY_STRING_ARRAY;
    }
    return tokens.stream().map(map -> map.get("token")).toArray(String[]::new);
  }

  /**
   * Executes the provided request and returns the response.
   * 
   * @param client
   * @param request
   * @return
   */
  public static Response executeRequest(RestClient client, Request request) {
    Response response = null;
    try {
      response = client.performRequest(request);
    } catch (Exception e) {
      if (e.getClass() != ResponseException.class) {
        throw new EsRequestException(e);
      }
      response = ((ResponseException) e).getResponse();
    }
    if (response.getStatusLine().getStatusCode() < 400) {
      return response;
    } else if (response.getStatusLine().getStatusCode() == 400) {
      // That really just is a bug in our code
      throw new EsException(getErrorMessage(response));
    } else if (response.getStatusLine().getStatusCode() == 429) {
      throw new TooManyRequestsException();
    }
    throw new EsRequestException(response);
  }

  /**
   * Executes the provided request at most 20 times, waiting 10 minutes in between, until it returns an error-free response.
   * 
   * @param client
   * @param request
   * @return
   */
  public static Response executeWithRetry(RestClient client, Request request) {
    return executeWithRetry(client, request, 20, 1000 * 60 * 10);
  }

  /**
   * Executes the provided request and returns the response. If the request fails because of an exception or because of an HTTP status code
   * greater than 399, the request is executed again. This might be less taxing for the Elasticsearch (client and/or server) than specifying
   * a long timeout for the request itself. So the idea is the specify a relative short timeout and let this method retry the specified
   * number of attempts until the response comes back error-free.
   * 
   * @param client
   * @param request
   * @param attempts
   * @param waitMillis
   * @return
   */
  public static Response executeWithRetry(RestClient client, Request request, int attempts, int waitMillis) {
    for (int i = 1; i <= attempts; ++i) {
      try {
        while (true) {
          try {
            return executeRequest(client, request);
          } catch (TooManyRequestsException e) {
            sleep(1000 * 60 * 5);
          }
        }
      } catch (EsRequestException e) {
        if (i == attempts) {
          throw e;
        } else if (LOG.isTraceEnabled()) {
          LOG.trace("{}. Attempt {} failed. Will attempt again after {} milliseconds",
              e.getMessage(), i, waitMillis);
        }
      }
      sleep(waitMillis);
    }
    throw new AssertionError("Should not get here");
  }

  /**
   * Can be used to asynchronously execute non-critical, "best effort" requests.
   * 
   * @param client
   * @param request
   */
  public static void executeAndForget(RestClient client, Request request) {
    client.performRequestAsync(request, new ResponseListener() {
      @Override
      public void onSuccess(Response response) {}
      @Override
      public void onFailure(Exception exception) {}
    });
  }

  /**
   * Whether or not Elasticsearch returned an OK response.
   * 
   * @param response
   * @return
   */
  public static boolean acknowlegded(Response response) {
    return readFromResponse(response, "acknowlegded");
  }

  /**
   * Returns the returns error message within the response body of an error response.
   * 
   * @param response
   * @return
   */
  public static String getErrorMessage(Response response) {
    Preconditions.checkArgument(response.getStatusLine().getStatusCode() >= 400, "not an error response");
    if (response.getEntity() == null) {
      return "No reason provided";
    }
    Map<String, Object> error = readFromResponse(response, "error");
    return error.get("reason").toString();
  }

  @SuppressWarnings("unchecked")
  public static <T> T readFromResponse(Response response, String property) {
    return (T) readResponse(response).get(property);
  }

  private static Map<String, Object> readResponse(Response response) {
    try {
      return EsModule.readIntoMap(response.getEntity().getContent());
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
    }
  }

}

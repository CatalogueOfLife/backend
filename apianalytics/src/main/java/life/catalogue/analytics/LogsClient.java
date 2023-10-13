package life.catalogue.analytics;

import life.catalogue.es.EsClientFactory;
import life.catalogue.es.EsConfig;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;


import static life.catalogue.analytics.EsConstants.*;

public class LogsClient implements Closeable {

  private final RestHighLevelClient highLevelClient;

  public LogsClient(EsConfig cfg) {
    highLevelClient = new RestHighLevelClient(new EsClientFactory(cfg).createClientBuilder());
  }

  public ExternalRequestsMetrics getExternalRequestsMetrics(
    String indexName, LocalDateTime startDatetime, LocalDateTime endDatetime) throws IOException {
    storeScriptsIfNeeded();
    return parseExternalRequestsMetricsResponse(
      highLevelClient.search(
        createExternalRequestsMetricsSearchRequest(indexName, startDatetime, endDatetime),
        HEADERS.get()));
  }

  public long getGbifPortalRequestsCount(
    String indexName, LocalDateTime startDatetime, LocalDateTime endDatetime) throws IOException {
    return highLevelClient
      .search(
        createGbifPortalRequestsSearchRequest(indexName, startDatetime, endDatetime),
        HEADERS.get())
      .getHits()
      .getTotalHits()
      .value;
  }

  public boolean indexExists(String indexName) throws IOException {
    return highLevelClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
  }

  private void storeScriptsIfNeeded() throws IOException {
    if (!scriptExists(REQUEST_PATTERN_SCRIPT_ID)) {
      boolean created = storeScript(REQUEST_PATTERN_SCRIPT_ID, REQUEST_PATTERN_SCRIPT_CODE);
      if (!created) {
        throw new IllegalStateException("Could not store ES script " + REQUEST_PATTERN_SCRIPT_ID);
      }
    }
    if (!scriptExists(AGENT_AGG_SCRIPT_ID)) {
      boolean created = storeScript(AGENT_AGG_SCRIPT_ID, AGENT_AGG_SCRIPT_CODE);
      if (!created) {
        throw new IllegalStateException("Could not store ES script " + AGENT_AGG_SCRIPT_ID);
      }
    }
  }

  private boolean scriptExists(String scriptId) throws IOException {
    try {
      return highLevelClient
               .getScript(new GetStoredScriptRequest(scriptId), RequestOptions.DEFAULT)
               .getId()
             != null;
    } catch (ElasticsearchStatusException ex) {
      if (ex.status() == RestStatus.NOT_FOUND) {
        return false;
      }
      throw ex;
    }
  }

  private boolean storeScript(String scriptId, String scriptSourceCode) throws IOException {
    XContentBuilder builder = XContentFactory.jsonBuilder();
    builder.startObject();
    builder.startObject("script");
    builder.field("lang", SCRIPT_LANG);
    builder.field("source", scriptSourceCode);
    builder.endObject();
    builder.endObject();

    return highLevelClient
      .putScript(
        new PutStoredScriptRequest()
          .id(scriptId)
          .content(BytesReference.bytes(builder), XContentType.JSON),
        RequestOptions.DEFAULT)
      .isAcknowledged();
  }

  private SearchRequest createExternalRequestsMetricsSearchRequest(
    String indexName, LocalDateTime startDatetime, LocalDateTime endDatetime) {
    SearchRequest esRequest = new SearchRequest();
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    esRequest.indices(indexName);
    esRequest.source(searchSourceBuilder);

    searchSourceBuilder.size(0);
    searchSourceBuilder.trackTotalHits(true);

    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

    // timestamp range
    boolQueryBuilder
      .filter()
      .add(QueryBuilders.rangeQuery(TIMESTAMP_FIELD).gte(startDatetime).lt(endDatetime));

    // filters
    boolQueryBuilder.filter().add(QueryBuilders.termQuery(HOST_FIELD, "prodapicache-vh.gbif.org"));
    boolQueryBuilder.filter().add(QueryBuilders.wildcardQuery(REQUEST_FIELD, "*api.gbif.org*"));

    // must_not
    boolQueryBuilder.mustNot().add(QueryBuilders.wildcardQuery(CLIENT_IP_FIELD, "130.225.43.*"));
    boolQueryBuilder.mustNot().add(QueryBuilders.wildcardQuery(REQUEST_FIELD, "*robots.txt"));
    boolQueryBuilder.mustNot().add(QueryBuilders.wildcardQuery(REQUEST_FIELD, "*favicon.ico"));
    boolQueryBuilder
      .mustNot()
      .add(QueryBuilders.termQuery(REFERRER_FIELD, "https://www.gbif.org/"));
    boolQueryBuilder.mustNot().add(QueryBuilders.termsQuery(AGENT_NAME_FIELD, BOTS));
    searchSourceBuilder.query(boolQueryBuilder);

    // aggs
    searchSourceBuilder.aggregation(
      AggregationBuilders.terms(COUNTRY_AGG).field(COUNTRY_FIELD).size(AGG_SIZE));
    searchSourceBuilder.aggregation(
      AggregationBuilders.terms(RESPONSE_AGG).field(RESPONSE_FIELD).size(AGG_SIZE));

    searchSourceBuilder.aggregation(
      AggregationBuilders.terms(AGENT_AGG)
                         .script(
                           new Script(ScriptType.STORED, null, AGENT_AGG_SCRIPT_ID, Collections.emptyMap()))
                         .size(AGG_SIZE));

    searchSourceBuilder.aggregation(
      AggregationBuilders.terms(REQUEST_PATTERN_AGG)
                         .script(
                           new Script(
                             ScriptType.STORED, null, REQUEST_PATTERN_SCRIPT_ID, Collections.emptyMap()))
                         .size(AGG_SIZE));

    return esRequest;
  }

  private ExternalRequestsMetrics parseExternalRequestsMetricsResponse(SearchResponse response) {
    ExternalRequestsMetrics externalRequestsMetrics = new ExternalRequestsMetrics();
    externalRequestsMetrics.setRequestsCount(response.getHits().getTotalHits().value);

    Map<String, Aggregation> aggs = response.getAggregations().asMap();

//  externalRequestsMetrics.setGeolocationAgg(
//    ((Terms) aggs.get(COUNTRY_AGG))
//      .getBuckets().stream()
//      .collect(
//        Collectors.toMap(
//          v -> Country.INTERNATIONAL_WATERS,//v -> Country.fromIsoCode(v.getKeyAsString()).orElse(Country.INTERNATIONAL_WATERS),
//          MultiBucketsAggregation.Bucket::getDocCount)
//      )
//  );

    externalRequestsMetrics.setAgentAgg(
      ((Terms) aggs.get(AGENT_AGG))
        .getBuckets().stream()
        .collect(
          Collectors.toMap(
            MultiBucketsAggregation.Bucket::getKeyAsString,
            MultiBucketsAggregation.Bucket::getDocCount)));

    externalRequestsMetrics.setResponseCodeAgg(
      ((Terms) aggs.get(RESPONSE_AGG))
        .getBuckets().stream()
        .collect(
          Collectors.toMap(
            v -> v.getKeyAsNumber().intValue(),
            MultiBucketsAggregation.Bucket::getDocCount)));

    externalRequestsMetrics.setRequestPatternAgg(
      ((Terms) aggs.get(REQUEST_PATTERN_AGG))
        .getBuckets().stream()
        .collect(
          Collectors.toMap(
            MultiBucketsAggregation.Bucket::getKeyAsString,
            MultiBucketsAggregation.Bucket::getDocCount)));

    return externalRequestsMetrics;
  }

  private SearchRequest createGbifPortalRequestsSearchRequest(
    String indexName, LocalDateTime startDatetime, LocalDateTime endDatetime) {
    SearchRequest esRequest = new SearchRequest();
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    esRequest.indices(indexName);
    esRequest.source(searchSourceBuilder);

    searchSourceBuilder.size(0);
    searchSourceBuilder.trackTotalHits(true);

    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

    // timestamp range
    boolQueryBuilder
      .filter()
      .add(QueryBuilders.rangeQuery(TIMESTAMP_FIELD).gte(startDatetime).lt(endDatetime));

    // filters
    boolQueryBuilder.filter().add(QueryBuilders.termQuery(HOST_FIELD, "prodapicache-vh.gbif.org"));
    boolQueryBuilder.filter().add(QueryBuilders.wildcardQuery(REQUEST_AGENT_FIELD, "GBIF-portal"));

    searchSourceBuilder.query(boolQueryBuilder);

    return esRequest;
  }

  @Override
  public void close() throws IOException {
    highLevelClient.close();
  }
}
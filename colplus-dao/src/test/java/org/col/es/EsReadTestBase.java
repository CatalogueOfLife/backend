package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.dsl.Query;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageWrapperConverter;
import org.col.es.name.search.NameUsageSearchService;
import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;

/**
 * Base class for tests that only read from ES. Does not provide postgres functionality and saves setup/initialization
 * time accordingly.
 */
public class EsReadTestBase {

  public static final String indexName = "name_usage_test";

  @ClassRule
  public static EsSetupRule esSetupRule = new EsSetupRule();

  protected EsConfig getEsConfig() {
    return esSetupRule.getEsConfig();
  }

  protected RestClient getEsClient() {
    return esSetupRule.getEsClient();
  }

  // Useful for @Before methods
  protected void destroyAndCreateIndex() {
    try {
      EsUtil.deleteIndex(getEsClient(), indexName);
      EsUtil.createIndex(getEsClient(), indexName, getEsConfig().nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected void indexRaw(Collection<NameUsageDocument> documents) {
    try {
      for (NameUsageDocument doc : documents) {
        EsUtil.insert(getEsClient(), indexName, doc);
      }
      EsUtil.refreshIndex(getEsClient(), indexName);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected void indexRaw(NameUsageDocument... documents) {
    indexRaw(Arrays.asList(documents));
  }

  protected List<NameUsageDocument> queryRaw(Query query) {
    EsSearchRequest esr = EsSearchRequest.emptyRequest().where(query);
    return new NameUsageSearchService(indexName, getEsClient()).getDocuments(esr);
  }

  protected NameUsageDocument toDocument(NameUsageWrapper nameUsage) {
    try {
      return new NameUsageWrapperConverter().toDocument(nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected List<NameUsageDocument> toDocuments(Collection<NameUsageWrapper> nameUsages) {
    return nameUsages.stream().map(this::toDocument).collect(Collectors.toList());
  }

  protected void index(NameUsageWrapper nameUsage) {
    indexRaw(toDocument(nameUsage));
  }

  protected void index(NameUsageWrapper... nameUsages) {
    indexRaw(toDocuments(Arrays.asList(nameUsages)));
  }

  protected void index(Collection<NameUsageWrapper> nameUsages) {
    indexRaw(toDocuments(nameUsages));
  }

  protected NameSearchResponse search(NameSearchRequest query) {
    return new NameUsageSearchService(indexName, getEsClient()).search(query, new Page(0, 1000));
  }

}

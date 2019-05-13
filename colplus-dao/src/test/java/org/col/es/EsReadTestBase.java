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
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;

/**
 * Base class for tests that only read from ES. Does not provide postgres functionality and saves setup/initialization time accordingly.
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

  protected void indexRaw(Collection<EsNameUsage> raw) {
    try {
      EsUtil.insert(getEsClient(), indexName, raw);
      EsUtil.refreshIndex(getEsClient(), indexName);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected void indexRaw(EsNameUsage raw) {
    indexRaw(Arrays.asList(raw));
  }

  protected EsNameUsage toDocument(NameUsageWrapper nameUsage) {
    try {
      return new NameUsageTransfer().toDocument(nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected List<EsNameUsage> toDocuments(Collection<NameUsageWrapper> nameUsages) {
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

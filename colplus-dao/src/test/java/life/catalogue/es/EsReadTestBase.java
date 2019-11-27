package life.catalogue.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import life.catalogue.es.EsConfig;
import life.catalogue.es.EsException;
import life.catalogue.es.EsUtil;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.model.NameUsageDocument;
import life.catalogue.es.name.NameUsageWrapperConverter;
import life.catalogue.es.name.search.NameUsageSearchServiceEs;
import life.catalogue.es.name.suggest.NameUsageSuggestionServiceEs;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;
import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;

import static java.util.stream.Collectors.toList;

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
      EsUtil.createIndex(getEsClient(), indexName, NameUsageDocument.class, getEsConfig().nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected void truncate() {
    try {
      EsUtil.truncate(getEsClient(), indexName);
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
    return new NameUsageSearchServiceEs(indexName, getEsClient()).getDocuments(esr);
  }

  protected List<NameUsageDocument> queryRaw(EsSearchRequest rawRequest) {
    return new NameUsageSearchServiceEs(indexName, getEsClient()).getDocuments(rawRequest);
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

  protected NameUsageSearchResponse search(NameUsageSearchRequest query) {
    return new NameUsageSearchServiceEs(indexName, getEsClient()).search(query, new Page(0, 1000));
  }

  protected NameUsageSuggestResponse suggest(NameUsageSuggestRequest query) {
    return new NameUsageSuggestionServiceEs(indexName, getEsClient()).suggest(query);
  }

  /**
   * Creates the requested number of name usages with all fields required to allow them to be indexed without NPEs and
   * other errors.
   * 
   * @param howmany
   * @return
   */
  protected List<NameUsageWrapper> createNameUsages(int howmany) {
    return IntStream.rangeClosed(1, howmany).mapToObj(this::createNameUsage).collect(toList());
  }

  protected List<NameUsageWrapper> createNameUsages(int first, int last) {
    return IntStream.rangeClosed(first, last).mapToObj(this::createNameUsage).collect(toList());
  }

  protected NameUsageWrapper createNameUsage(int seqno) {
    Taxon t = TestEntityGenerator.newTaxon(EsSetupRule.DATASET_KEY, "t" + seqno, RandomUtils.randomSpecies());
    return new NameUsageWrapper(t);
  }

}

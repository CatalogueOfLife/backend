package life.catalogue.es;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.io.TempFile;
import life.catalogue.es.search.NameUsageSearchServiceEs;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Base class for tests that want to read/write to both Postgres and Elasticsearch.
 */
public abstract class EsPgTestBase {

  protected static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private static final Logger LOG = LoggerFactory.getLogger(EsPgTestBase.class);

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule();

  @ClassRule
  public static final EsSetupRule esSetupRule = new EsSetupRule();

  @Before
  public void before() throws Throwable {
    try {
      LOG.debug("Dumping test index \"{}\"", esSetupRule.getEsConfig().index);
      EsUtil.deleteIndex(esSetupRule.getClient(), esSetupRule.getEsConfig().index);
      LOG.debug("Creating test index \"{}\"", esSetupRule.getEsConfig().index);
      EsUtil.createIndex(esSetupRule.getClient(), esSetupRule.getEsConfig().index);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @After
  public void after() {
    LOG.debug("Test index \"{}\" kept around for inspection", esSetupRule.getEsConfig().index);
  }

  protected NameUsageIndexServiceEs createIndexService() {
    return new NameUsageIndexServiceEs(
        esSetupRule.getClient(),
        esSetupRule.getEsConfig(),
        TempFile.directoryFile(),
        SqlSessionFactoryRule.getSqlSessionFactory());
  }

  protected NameUsageSearchServiceEs createSearchService() {
    return new NameUsageSearchServiceEs(esSetupRule.getEsConfig().index.name, esSetupRule.getClient());
  }

  protected NameUsageSearchResponse search(NameUsageSearchRequest query) {
    return createSearchService().search(query, new Page(Page.MAX_LIMIT));
  }

  /**
   * Executes the provided raw ES query against the index and converts results back to NameUsageSearchResponse.
   */
  protected NameUsageSearchResponse query(Query query) throws IOException {
    String indexName = esSetupRule.getEsConfig().index.name;
    EsUtil.refreshIndex(esSetupRule.getClient(), indexName);
    SearchRequest searchRequest = SearchRequest.of(s -> s
      .index(indexName)
      .query(query)
      .size(Page.MAX_LIMIT)
      .trackTotalHits(th -> th.enabled(true))
    );
    List<EsNameUsage> docs = createSearchService().getDocuments(searchRequest);
    List<NameUsageWrapper> wrappers = new ArrayList<>();
    for (EsNameUsage doc : docs) {
      NameUsageWrapper nuw = NameUsageWrapperConverter.decode(doc.getPayload());
      NameUsageWrapperConverter.enrichPayload(nuw, doc);
      wrappers.add(nuw);
    }
    return new NameUsageSearchResponse(new Page(Page.MAX_LIMIT), wrappers.size(), wrappers, Collections.emptyMap());
  }

}

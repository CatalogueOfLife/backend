package life.catalogue.es;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.common.io.TempFile;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;

import java.io.IOException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      LOG.debug("Dumping test index \"{}\"", esSetupRule.getEsConfig().nameUsage);
      EsUtil.deleteIndex(esSetupRule.getClient(), esSetupRule.getEsConfig().nameUsage);
      LOG.debug("Creating test index \"{}\"", esSetupRule.getEsConfig().nameUsage);
      EsUtil.createIndex(esSetupRule.getClient(), EsNameUsage.class, esSetupRule.getEsConfig().nameUsage);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @After
  public void after() {
    LOG.debug("Test index \"{}\" kept around for inspection", esSetupRule.getEsConfig().nameUsage);
  }

  protected NameUsageIndexServiceEs createIndexService() {
    return new NameUsageIndexServiceEs(
        esSetupRule.getClient(),
        esSetupRule.getEsConfig(),
        TempFile.directoryFile(),
        SqlSessionFactoryRule.getSqlSessionFactory());
  }

  protected NameUsageSearchServiceEs createSearchService() {
    return new NameUsageSearchServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
  }

  protected NameUsageSearchResponse search(NameUsageSearchRequest query) {
    return createSearchService().search(query, new Page(Page.MAX_LIMIT));
  }

  /**
   * Executes the provided query against the text index. The number of returned documents is capped on {Page#MAX_LIMIT Page.MAX_LIMIT}, so
   * make sure the provided query will yield less documents.
   */
  protected NameUsageSearchResponse query(Query query) throws IOException {
    EsSearchRequest req = EsSearchRequest.emptyRequest().where(query).size(Page.MAX_LIMIT);
    return createSearchService().search(esSetupRule.getEsConfig().nameUsage.name, req, new Page(Page.MAX_LIMIT));
  }

}

package org.col.es;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.ibatis.session.SqlSession;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Page;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchResponse;
import org.col.db.PgSetupRule;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TestDataRule;
import org.col.es.EsUtil;
import org.col.es.name.index.NameUsageIndexService;
import org.col.es.name.index.NameUsageIndexServiceEs;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

/**
 * Base class for tests that want to read/write to both Postgres and Elasticsearch.
 */
public class EsReadWriteTestBase extends ExternalResource {
  
  private static final Logger LOG = LoggerFactory.getLogger(EsReadWriteTestBase.class);


  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule();

  @ClassRule
  public static final EsSetupRule esSetupRule = new EsSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();
  
  @Before
  public void before() throws Throwable {
    super.before();
    try {
      LOG.debug("Dumping test index \"{}\"", EsSetupRule.TEST_INDEX);
      EsUtil.deleteIndex(esSetupRule.getEsClient(), EsSetupRule.TEST_INDEX);
      LOG.debug("Creating test index \"{}\"", EsSetupRule.TEST_INDEX);
      EsUtil.createIndex(esSetupRule.getEsClient(), EsSetupRule.TEST_INDEX, esSetupRule.getEsConfig().nameUsage);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @After
  public void after() {
    LOG.debug("Test index \"{}\" kept around for inspection", EsSetupRule.TEST_INDEX);
  }

  
  protected NameUsageIndexService createIndexService() {
    return new NameUsageIndexServiceEs(
        esSetupRule.getEsClient(),
        esSetupRule.getEsConfig(),
        PgSetupRule.getSqlSessionFactory(),
        EsSetupRule.TEST_INDEX);
  }

  protected NameUsageSearchService createSearchService() {
    return new NameUsageSearchService(EsSetupRule.TEST_INDEX, esSetupRule.getEsClient());
  }

  /**
   * Creates the specified amount of taxa and insert them into Postgres. The taxa all belong to EsSetupRule.DATASET_KEY. Their ids are "t1",
   * "t2" ... "t${howmany}". Their name ids are "t1_name_id", "t2_name_id" ... "t${howmany}_name_id".
   * 
   * @param howmany
   * @return
   */
  protected List<Taxon> createPgTaxa(int howmany) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false)) {
      NameMapper nMapper = session.getMapper(NameMapper.class);
      TaxonMapper tMapper = session.getMapper(TaxonMapper.class);
      List<Taxon> taxa = createTaxa(howmany);
      for (Taxon t : taxa) {
        nMapper.create(t.getName());
        tMapper.create(t);
      }
      session.commit();
      return taxa;
    }
  }

  /**
   * Executes the provided query against the text index. The number of returned documents is capped on {Page#MAX_LIMIT Page.MAX_LIMIT}, so
   * make sure the provided query will yield less documents.
   */
  protected NameSearchResponse query(Query query) throws IOException {
    EsSearchRequest req = EsSearchRequest.emptyRequest().where(query).size(Page.MAX_LIMIT);
    return createSearchService().search(EsSetupRule.TEST_INDEX, req, new Page(Page.MAX_LIMIT));
  }

  /**
   * Creates the specified number of taxa. The first taxon will have id "t1", the last "t${howmany}". For each taxon a unique name is
   * created with a random scientific name. The first name will have id "t1_name_id", the last t${howmany}_name_id".
   * 
   * @param howmany
   * @return
   */
  protected List<Taxon> createTaxa(int howmany) {
    return IntStream.rangeClosed(1, howmany).mapToObj(this::createTaxon).collect(toList());
  }

  /**
   * Creates the specified nmber of taxa. The first taxon will have id "t${first}", the last "t${last}"
   * 
   * @param first
   * @param last
   * @return
   */
  protected List<Taxon> createTaxa(int first, int last) {
    return IntStream.rangeClosed(first, last).mapToObj(this::createTaxon).collect(toList());
  }

  protected Taxon createTaxon(int seqno) {
    return TestEntityGenerator.newTaxon(EsSetupRule.DATASET_KEY, "t" + seqno, RandomUtils.randomSpecies());
  }

}

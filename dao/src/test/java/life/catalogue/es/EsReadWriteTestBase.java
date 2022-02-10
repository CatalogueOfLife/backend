package life.catalogue.es;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.ibatis.session.SqlSession;
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

  protected static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private static final Logger LOG = LoggerFactory.getLogger(EsReadWriteTestBase.class);

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule(true);

  @ClassRule
  public static final EsSetupRule esSetupRule = new EsSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  @Before
  public void before() throws Throwable {
    super.before();
    try {
      LOG.debug("Dumping test index \"{}\"", esSetupRule.getEsConfig().nameUsage);
      EsUtil.deleteIndex(esSetupRule.getClient(), esSetupRule.getEsConfig().nameUsage);
      LOG.debug("Creating test index \"{}\"", esSetupRule.getEsConfig().nameUsage);
      EsUtil.createIndex(esSetupRule.getClient(),
          EsNameUsage.class,
          esSetupRule.getEsConfig().nameUsage);
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
        PgSetupRule.getSqlSessionFactory());
  }

  protected NameUsageSearchServiceEs createSearchService() {
    return new NameUsageSearchServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
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

  /**
   * Creates the specified number of taxa. The taxon will be created using TestEntityGenerator.newTaxon(datasetKey,id,scientificName). The
   * first taxon will have id "t1", the last "t${howmany}". For each taxon a unique name is created with a random scientific name. The first
   * name will have id "t1_name_id", the last t${howmany}_name_id".
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

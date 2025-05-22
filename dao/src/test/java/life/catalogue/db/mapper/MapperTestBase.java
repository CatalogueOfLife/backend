package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityUnmodifiedRule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;

import java.sql.Connection;
import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.ClassRule;
import org.junit.Rule;

import com.google.common.base.Preconditions;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public abstract class MapperTestBase<M> {
  static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  protected final Class<M> mapperClazz;
  protected final static int appleKey = TestDataRule.APPLE.key;

  @ClassRule
  public static SqlSessionFactoryRule pgRule = new PgSetupRule();
  //public static SqlSessionFactoryRule pgRule = new PgConnectionRule("col", "postgres", "postgres");

  @Rule
  public final TestEntityUnmodifiedRule unmodifiedRule = new TestEntityUnmodifiedRule();

  @Rule
  public final TestDataRule testDataRule;

  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();


  public MapperTestBase() {
    this(null, TestDataRule.empty());
  }

  public MapperTestBase(Class<M> mapperClazz) {
    this(mapperClazz, TestDataRule.apple());
  }

  public MapperTestBase(Class<M> mapperClazz, TestDataRule testDataRule) {
    this.mapperClazz = mapperClazz;
    this.testDataRule = testDataRule;
  }
  
  public M mapper() {
    return testDataRule.getMapper(mapperClazz);
  }
  
  public <X> X mapper(Class<X> clazz) {
    return testDataRule.getMapper(clazz);
  }

  public Connection connection() {
    return testDataRule.getSqlSession().getConnection();
  }

  public SqlSession session() {
    return testDataRule.getSqlSession();
  }

  public void commit() {
    testDataRule.commit();
  }
  
  protected void generateDatasetImport(int datasetKey) {
    commit();
    createSuccess(datasetKey, Users.TESTER);
    commit();
  }

  void insertName(Name n) {
    mapper(NameMapper.class).create(n);
  }

  void insertTaxon(Taxon t) {
    mapper(NameMapper.class).create(t.getName());
    mapper(TaxonMapper.class).create(t);
  }
  
  void insertSynonym(Synonym s) {
    mapper(NameMapper.class).create(s.getName());
    mapper(SynonymMapper.class).create(s);
  }

  public DatasetImport createSuccess(int datasetKey, int user) {
    DatasetImportDao did = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    return createSuccess(datasetKey, user, did);
  }

  /**
   * Generates new metrics and persists them as a new successful import record.
   */
  public static DatasetImport createSuccess(int datasetKey, int user, DatasetImportDao did) {
    DatasetImport di = did.generateMetrics(datasetKey, user);
    di.setDatasetKey(datasetKey);
    di.setCreatedBy(user);
    di.setState(ImportState.FINISHED);
    di.setJob(MapperTestBase.class.getSimpleName());
    di.setStarted(LocalDateTime.now());
    di.setDownload(LocalDateTime.now());
    di.setFinished(LocalDateTime.now());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetImportMapper dim = session.getMapper(DatasetImportMapper.class);

      Dataset d = Preconditions.checkNotNull(dm.get(datasetKey), "Dataset "+datasetKey+" does not exist");
      DatasetSettings ds = dm.getSettings(datasetKey);
      di.setDownloadUri(ds.getURI(Setting.DATA_ACCESS));
      di.setOrigin(d.getOrigin());
      di.setFormat(ds.getEnum(Setting.DATA_FORMAT));
      dim.create(di);
    }
    // also update dataset with attempt
    did.updateDatasetLastAttempt(di);
    return di;
  }

  protected void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }
}
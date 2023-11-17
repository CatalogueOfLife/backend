package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.db.PgConnectionRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public abstract class DaoTestBase {
  
  SqlSession session;
  static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();
  //public static SqlSessionFactoryRule pgRule = new PgConnectionRule("clb", "postgres", "postgres");

  @Rule
  public TestDataRule testDataRule;
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  
  public DaoTestBase(){
    this(TestDataRule.apple());
  }
  
  public DaoTestBase(TestDataRule initRule){
    this.testDataRule = initRule;
  }

  
  @Before
  public void initSession() {
    session = session();
  }
  
  @After
  public void cleanup() {
    session.close();
  }

  protected static SqlSessionFactory factory() {
    return SqlSessionFactoryRule.getSqlSessionFactory();
  }
  
  protected SqlSession session() {
    return testDataRule.getSqlSession();
  }
  
  public <T> T mapper(Class<T> mapperClazz) {
    return session().getMapper(mapperClazz);
  }
  
  public void commit() {
    testDataRule.commit();
  }
  
  public DSID key(int datasetKey, String id) {
    return new DSIDValue(datasetKey, id);
  }
}

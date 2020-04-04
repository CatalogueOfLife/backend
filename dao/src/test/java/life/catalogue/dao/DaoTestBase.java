package life.catalogue.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public abstract class DaoTestBase {
  
  SqlSession session;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule;
  
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
    return PgSetupRule.getSqlSessionFactory();
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

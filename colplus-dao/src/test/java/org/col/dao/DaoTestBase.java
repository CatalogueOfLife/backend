package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DSID;
import org.col.api.model.DSIDValue;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
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

  protected SqlSessionFactory factory() {
    return pgSetupRule.getSqlSessionFactory();
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

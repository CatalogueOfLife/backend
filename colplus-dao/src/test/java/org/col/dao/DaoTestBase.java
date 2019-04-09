package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.db.PgSetupRule;
import org.col.db.mapper.InitMybatisRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public abstract class DaoTestBase {
  
  SqlSession session;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final InitMybatisRule initMybatisRule;
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  
  public DaoTestBase(){
    this(InitMybatisRule.apple());
  }
  
  public DaoTestBase(InitMybatisRule initRule){
    this.initMybatisRule = initRule;
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
    return initMybatisRule.getSqlSession();
  }
  
  public <T> T mapper(Class<T> mapperClazz) {
    return session().getMapper(mapperClazz);
  }
  
  public void commit() {
    initMybatisRule.commit();
  }
}

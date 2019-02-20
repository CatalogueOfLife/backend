package org.col.db.mapper;

import org.apache.ibatis.session.SqlSession;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public abstract class MapperTestBase<T> {
  
  
  private final Class<T> mapperClazz;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final InitMybatisRule initMybatisRule;
  
  public MapperTestBase(Class<T> mapperClazz) {
    this(mapperClazz, InitMybatisRule.apple());
  }
  
  public MapperTestBase(Class<T> mapperClazz, InitMybatisRule initMybatisRule) {
    this.mapperClazz = mapperClazz;
    this.initMybatisRule = initMybatisRule;
  }
  
  public T mapper() {
    return initMybatisRule.getMapper(mapperClazz);
  }
  
  public <X> X mapper(Class<X> clazz) {
    return initMybatisRule.getMapper(clazz);
  }
  
  public SqlSession session() {
    return initMybatisRule.getSqlSession();
  }

  public void commit() {
    initMybatisRule.commit();
  }
  
  protected void generateDatasetImport(int datasetKey) {
    commit();
    DatasetImportDao dao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory());
    dao.createSuccess(datasetKey);
    commit();
  }
  
  protected void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }
  
}
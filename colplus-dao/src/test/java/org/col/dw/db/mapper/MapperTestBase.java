package org.col.dw.db.mapper;

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
  public InitMybatisRule initMybatisRule = InitMybatisRule.squirrels();

  public MapperTestBase(Class<T> mapperClazz) {
    this.mapperClazz = mapperClazz;
  }

  public T mapper() {
    return initMybatisRule.getMapper(mapperClazz);
  }

  public void commit() {
    initMybatisRule.commit();
  }


}
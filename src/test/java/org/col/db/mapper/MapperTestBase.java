package org.col.db.mapper;

import org.col.api.Dataset;
import org.junit.ClassRule;
import org.junit.Rule;

import java.util.Random;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public class MapperTestBase<T> {

  public final static Random RND = new Random();
  public final static Dataset D1 = new Dataset();
  public final static Dataset D2 = new Dataset();

  static {
    D1.setKey(1);
    D2.setKey(2);
  }

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
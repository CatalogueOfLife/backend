package org.col.db.mapper;

import org.col.api.Dataset;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public class MapperTestBase<T> {

  public final static Dataset d1 = new Dataset();
  public final static Dataset d2 = new Dataset();

  static {
    d1.setKey(1);
    d2.setKey(2);
  }

  T mapper;

  @ClassRule
  public static PgMybatisRule pgMybatisRule = new PgMybatisRule();

  @Rule
  public DbInitRule dbInitRule = DbInitRule.squirrels();

  public MapperTestBase(Class<T> mapperClazz) {
    mapper = pgMybatisRule.getMapper(mapperClazz);
  }

  public void commit() {
    pgMybatisRule.commit();
  }

}
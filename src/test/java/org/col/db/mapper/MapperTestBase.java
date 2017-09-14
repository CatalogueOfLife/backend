package org.col.db.mapper;

import org.junit.ClassRule;
import org.junit.Rule;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public class MapperTestBase<T> {

  T mapper;

  @ClassRule
  public static PgMybatisRule pgMybatisRule = new PgMybatisRule();

  @Rule
  public DbInitRule dbInitRule = DbInitRule.empty();

  public MapperTestBase(Class<T> mapperClazz) {
    mapper = pgMybatisRule.getMapper(mapperClazz);
  }

  public void commit() {
    pgMybatisRule.commit();
  }

}
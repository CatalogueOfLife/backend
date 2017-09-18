package org.col.db.mapper;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Test suite for all mapper tests that share the same embedded postgres instance
 * for performance reasons. Start/stopping postgres takes 5-10 seconds alone.
 *
 * Note: the test suite is disabled for now to simplify tests.
 * For quicker tests we can enable it at any stage again.
 */
@Ignore
@RunWith(Suite.class)
@Suite.SuiteClasses({NameMapperTest.class, SerialMapperTest.class})
public class MapperTestSuite {

  @ClassRule
  public static PgMybatisRule pgMybatisRule = new PgMybatisRule();

}

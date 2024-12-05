package life.catalogue.junit;

import java.sql.Connection;
import java.sql.Statement;

import life.catalogue.db.SqlSessionFactoryWithPath;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SqlSessionFactoryWithPathTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Test
  public void testSearchPath() throws Exception {
    final String schema = "testschema";
    final String pub = "public";

    try (Connection c = pgSetupRule.connect();
         Statement st = c.createStatement()
    ) {
      // setup build schema
      st.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", pub));
      st.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schema));
      st.execute(String.format("CREATE SCHEMA %s", pub));
      st.execute(String.format("CREATE SCHEMA %s", schema));
      st.execute(String.format("CREATE TABLE %s.woman (key serial, name text)", pub));
      st.execute(String.format("CREATE TABLE %s.man (key serial, name text)", pub));
      st.execute(String.format("CREATE TABLE %s.man (key serial, name text)", schema));
      st.execute(String.format("INSERT INTO %s.man (name) SELECT 'Man ' || x FROM generate_series(1, 10) x", pub));
      st.execute(String.format("INSERT INTO %s.woman (name) SELECT 'Woman ' || x FROM generate_series(1, 12) x", pub));
    }

    SqlSessionFactory factory = new SqlSessionFactoryWithPath(SqlSessionFactoryRule.getSqlSessionFactory(), schema);
    factory.getConfiguration().addMapper(ManMapper.class);

    try (SqlSession session = factory.openSession()) {
      ManMapper mapper = session.getMapper(ManMapper.class);
      assertEquals(0, mapper.countMan());
      assertEquals(12, mapper.countWoman());
    }

  }

  interface ManMapper {
    @Select("select count(*) from man")
    int countMan();

    @Select("select count(*) from woman")
    int countWoman();
  }
}
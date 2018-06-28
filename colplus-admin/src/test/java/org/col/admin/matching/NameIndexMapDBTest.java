package org.col.admin.matching;

import java.sql.SQLException;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.IssueContainer;
import org.col.api.model.Name;
import org.col.db.mapper.InitMybatisRule;
import org.col.db.mapper.PgSetupRule;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameIndexMapDBTest {
  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple();

  SqlSession session() {
    return initMybatisRule.getSqlSession();
  }

  @Test
  public void loadApple() throws SQLException {
    ni = NameIndexFactory.memory(1, PgSetupRule.getSqlSessionFactory());
    assertEquals(3, ni.size());

    assertEquals(4, (int) query(Rank.SPECIES, "Larus erfundus").getName().getKey());
    assertEquals(4, (int) query(Rank.SPECIES, "Larus erfunda").getName().getKey());
    assertEquals(3, (int) query(Rank.SPECIES, "Larus fusca").getName().getKey());
    assertEquals(2, (int) query(Rank.SPECIES, "Larus fuscus").getName().getKey());
  }

  private NameMatch query(Rank rank, String name) {
    Name q = NameParser.PARSER.parse(name, rank, IssueContainer.VOID).get().getName();
    return ni.match(q, false, true);
  }
}
package org.col.db;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import com.google.common.base.Joiner;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class PgSetupRuleTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  
  @Test
  public void rankEnum() throws Exception {
    try (Connection c = pgSetupRule.connect()) {
      // make sure all ranks from our enum are present in postgres
      PreparedStatement pst = c.prepareStatement("SELECT ?::rank");
      for (Rank r : Rank.values()){
        pst.setString(1, r.name().toLowerCase());
        pst.execute();
      }
      
      // ... and vice versa
      String expected = "{" + Joiner.on(",").join(Rank.values()).toLowerCase() + "}";
      Statement st = c.createStatement();
      st.execute("select enum_range(null::rank)");
      st.getResultSet().next();
      Array arr = st.getResultSet().getArray(1);
      assertEquals(expected, arr.toString() );
    }
  }
  
  @Test
  public void rankEnumSql() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TYPE rank AS ENUM (\n");
    for (Rank r : Rank.values()){
      sb.append("  '");
      sb.append(r.name().toLowerCase());
      sb.append("'");
      if (r != Rank.UNRANKED) {
        sb.append(",\n");
      }
    }
    sb.append("\n);\n");
    System.out.println(sb);
  }
  
}
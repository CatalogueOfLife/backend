package life.catalogue.db.type2;

import life.catalogue.api.model.SimpleName;
import life.catalogue.db.PgSetupRule;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;
import org.postgresql.jdbc.PgConnection;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SimpleNameArrayTypeHandlerTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Test
  public void toList() throws SQLException {
    try (PgConnection c = pgSetupRule.connect()) {
      Array array = c.createArrayOf("text", new String[]{
        "(k6,KINGDOM,Plantae)",
        "(\"fhsdfgh,; h2\",PHYLUM,\"Tracheophyta, 1677\")",
        "(\"id\"\"123\"\"\",GENUS,\"Bern'd, (1973)\")",
        "(,,Bernd)"
      });
      SimpleNameArrayTypeHandler snh = new SimpleNameArrayTypeHandler();
      List<SimpleName> names = snh.toObj(array);
      assertSN(names.get(0), "k6", Rank.KINGDOM, "Plantae");
      assertSN(names.get(1), "fhsdfgh,; h2", Rank.PHYLUM, "Tracheophyta, 1677");
      assertSN(names.get(2), "id\"123\"", Rank.GENUS, "Bern'd, (1973)");
      assertSN(names.get(3), null, null, "Bernd");
    }
  }
  
  private static void assertSN(SimpleName sn, String id, Rank rank, String name){
    assertEquals(id, sn.getId());
    assertEquals(rank, sn.getRank());
    assertEquals(name, sn.getName());
    assertNull(sn.getAuthorship());
    assertNull(sn.getStatus());
    assertNull(sn.getParent());
  }
}
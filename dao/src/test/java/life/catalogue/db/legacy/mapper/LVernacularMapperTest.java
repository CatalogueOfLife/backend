package life.catalogue.db.legacy.mapper;

import life.catalogue.db.LookupTables;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.legacy.model.LCommonName;
import life.catalogue.db.legacy.model.LName;
import life.catalogue.db.mapper.MapperTestBase;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

public class LVernacularMapperTest extends MapperTestBase<LVernacularMapper> {

  int datasetKey = TestDataRule.TestData.APPLE.key;

  public LVernacularMapperTest() {
    super(LVernacularMapper.class);
  }


  @Test
  public void count() {
    // 1 Apple
    // 2 Apfel
    // 3 Meeuw
    assertEquals(2, mapper().count(datasetKey, true, "Ap"));
    assertEquals(1, mapper().count(datasetKey, true, "mee"));
    assertEquals(0, mapper().count(datasetKey, false, "Apf"));
    assertEquals(1, mapper().count(datasetKey, false, "Apfel"));
  }

  @Test
  public void search() throws IOException, SQLException {
    LookupTables.recreateTables(session().getConnection());
    mapper().search(datasetKey, false, "Apfel" ,0 ,2).forEach(this::isVernacular);
  }

  void isVernacular(LName n) {
    assertEquals(LCommonName.class, n.getClass());
  }
}
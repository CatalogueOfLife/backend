package life.catalogue.db.mapper.legacy.mapper;

import life.catalogue.db.LookupTables;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.legacy.LVernacularMapper;
import life.catalogue.db.mapper.legacy.model.LCommonName;
import life.catalogue.db.mapper.legacy.model.LName;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

public class LVernacularMapperTest extends MapperTestBase<LVernacularMapper> {

  int datasetKey = TestDataRule.APPLE.key;

  public LVernacularMapperTest() {
    super(LVernacularMapper.class);
  }


  @Before
  public void init () throws IOException, SQLException {
    LookupTables.recreateTables(session().getConnection());
  }

  @Test
  public void count() {
    // 1 Apple
    // 2 Apfel
    // 3 Meeuw
    assertEquals(2, mapper().count(datasetKey, true, "Ap"));
    assertEquals(2, mapper().count(datasetKey, true, "AP"));
    assertEquals(2, mapper().count(datasetKey, true, "ap"));
    assertEquals(1, mapper().count(datasetKey, true, "mee"));
    assertEquals(0, mapper().count(datasetKey, false, "Apf"));
    assertEquals(1, mapper().count(datasetKey, false, "Apfel"));
  }

  @Test
  public void search() throws IOException, SQLException {
    mapper().search(datasetKey, false, "Apfel" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, false, "Apfel'" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, false, "Apfel's" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, false, "Apf\\" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, false, "Apfel\\" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, false, "Ap_el" ,0 ,2).forEach(this::isVernacular);

    mapper().search(datasetKey, true, "Apfel" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, true, "Apfel'" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, true, "Apf\\" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, true, "Apfel's" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, true, "Apfel\\" ,0 ,2).forEach(this::isVernacular);
    mapper().search(datasetKey, true, "Ap_el" ,0 ,2).forEach(this::isVernacular);
  }

  void isVernacular(LName n) {
    assertEquals(LCommonName.class, n.getClass());
  }
}
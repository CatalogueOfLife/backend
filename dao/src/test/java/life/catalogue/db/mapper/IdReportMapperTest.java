package life.catalogue.db.mapper;

import life.catalogue.api.model.IdReportEntry;

import org.junit.Test;

import static org.junit.Assert.*;
import static life.catalogue.api.vocab.IdReportType.*;

public class IdReportMapperTest extends MapperTestBase<IdReportMapper> {

  public IdReportMapperTest() {
    super(IdReportMapper.class);
  }


  @Test
  public void roundtrip() throws Exception {
    IdReportEntry id = new IdReportEntry(appleKey, CREATED, 77);
    // test create
    mapper().create(id);
    commit();

    // test get
    var obj = mapper().get(appleKey, 77);
    assertEquals(id, obj);

    // test processDataset
    mapper().processDataset(999).forEach(o -> fail("should never reach here"));
    mapper().processDataset(appleKey).forEach(o -> assertEquals(id, o));
  }

}
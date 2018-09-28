package org.col.db.mapper;

import org.col.api.model.ColSource;
import org.col.api.model.Sector;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.*;

public class SectorMapperTest extends MapperTestBase<SectorMapper> {

  private ColSource source;

  public SectorMapperTest() {
    super(SectorMapper.class);
  }

  @Before
  public void initSource() {
    source = ColSourceMapperTest.create();
    mapper(ColSourceMapper.class).create(source);
  }

  @Test
  public void roundtrip() throws Exception {
    Sector d1 = create(source.getKey());
    mapper().create(d1);

    commit();

    Sector d2 = mapper().get(d1.getKey());
    // remove newly set property
    d2.setCreated(null);

    assertEquals(d1, d2);
  }

  @Test
  public void delete() throws Exception {
    Sector d1 = create(source.getKey());
    mapper().create(d1);

    commit();

    // not deleted yet
    Sector d = mapper().get(d1.getKey());
    assertNotNull(d.getCreated());

    // physically delete
    mapper().delete(d1.getKey());
    assertNull(mapper().get(d1.getKey()));
  }

  public static Sector create(int sourceKey) {
    Sector d = new Sector();
    d.setColSourceKey(sourceKey);
    d.setRoot(newNameRef());
    d.setAttachment(newNameRef());
    return d;
  }

}
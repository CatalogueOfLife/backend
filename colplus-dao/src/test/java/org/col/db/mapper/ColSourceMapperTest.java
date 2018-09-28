package org.col.db.mapper;

import java.net.URI;
import java.time.LocalDate;

import org.col.api.RandomUtils;
import org.col.api.model.ColSource;
import org.col.api.vocab.DatasetType;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class ColSourceMapperTest extends MapperTestBase<ColSourceMapper> {

  public ColSourceMapperTest() {
    super(ColSourceMapper.class);
  }

  @Test
  public void roundtrip() throws Exception {
    ColSource d1 = create();
    mapper().create(d1);

    commit();

    ColSource d2 = mapper().get(d1.getKey(), false);
    // remove newly set property
    d2.setCreated(null);

    assertEquals(d1, d2);
  }

  @Test
  public void delete() throws Exception {
    ColSource d1 = create();
    mapper().create(d1);

    commit();

    // not deleted yet
    ColSource d = mapper().get(d1.getKey(), false);
    assertNotNull(d.getCreated());

    // physically delete
    mapper().delete(d1.getKey());
    assertNull(mapper().get(d1.getKey(), false));
  }

  public static ColSource create() {
    ColSource d = new ColSource();
    d.setDatasetKey(DATASET11.getKey());
    d.setCoverage(DatasetType.GLOBAL);
    d.setTitle(RandomUtils.randomString(80));
    d.setDescription(RandomUtils.randomString(500));
    for (int i = 0; i < 8; i++) {
      d.getAuthorsAndEditors().add(RandomUtils.randomString(100));
    }
    d.setContactPerson("Hans Peter");
    d.setReleased(LocalDate.now());
    d.setVersion("v123");
    d.setHomepage(URI.create("https://www.gbif.org/dataset/13"));
    d.setOrganisation("my org");
    d.setNamesCount(23456);
    d.setAlias("ILDIS");
    return d;
  }

}
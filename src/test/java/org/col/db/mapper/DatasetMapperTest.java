package org.col.db.mapper;

import org.col.api.Dataset;
import org.col.api.vocab.DataFormat;
import org.gbif.utils.text.StringUtils;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class DatasetMapperTest extends MapperTestBase<DatasetMapper> {

  public DatasetMapperTest() {
    super(DatasetMapper.class);
  }

  private Dataset create() throws Exception {
    Dataset d = new Dataset();
    d.setGbifKey(UUID.randomUUID());
    d.setTitle(StringUtils.randomString(80));
    d.setDescription(StringUtils.randomString(500));
    d.setAlias(StringUtils.randomString(10));
    d.setAuthorsAndEditors(StringUtils.randomString(100));
    d.setCompleteness(RND.nextInt(100));
    d.setConfidence(RND.nextInt(5));
    d.setContactPerson("Hans Peter");
    d.setCoverage(StringUtils.randomString(75));
    d.setTaxonomicCoverage("tax cover");
    d.setDataAccess(URI.create("https://api.gbif.org/v1/dataset/"+d.getGbifKey()));
    d.setDataFormat(DataFormat.SQL);
    d.setReleaseDate(LocalDate.now());
    d.setVersion("v123");
    d.setGroupName("Affen");
    d.setHomepage(URI.create("https://www.gbif.org/dataset/"+d.getGbifKey()));
    d.setNotes("my notes");
    d.setOrganisation("my org");
    return d;
  }

  @Test
  public void roundtrip() throws Exception {
    Dataset d1 = create();
    mapper().insert(d1);

    commit();

    Dataset d2 = mapper().get(d1.getKey());
    // remove newly set property
    d2.setCreated(null);

    assertEquals(d1, d2);
  }

  @Test
  public void delete() throws Exception {
    Dataset d1 = create();
    mapper().insert(d1);

    commit();

    // not deleted yet
    Dataset d = mapper().get(d1.getKey());
    assertNull(d.getDeleted());
    assertNotNull(d.getCreated());

    // mark deleted
    mapper().delete(d1.getKey());
    d = mapper().get(d1.getKey());
    assertNotNull(d.getDeleted());
  }

}
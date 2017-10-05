package org.col.db.mapper;

import com.google.common.collect.Lists;
import org.col.api.Dataset;
import org.col.api.Page;
import org.col.api.vocab.DataFormat;
import org.gbif.utils.text.StringUtils;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

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
    mapper().create(d1);

    commit();

    Dataset d2 = mapper().get(d1.getKey());
    // remove newly set property
    d2.setCreated(null);

    assertEquals(d1, d2);
  }

  @Test
  public void delete() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);

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

  @Test
  public void count() throws Exception {
    assertEquals(2, mapper().count());

    mapper().create(create());
    mapper().create(create());
    // even thogh not committed we are in the same session so we see the new datasets already
    assertEquals(4, mapper().count());

    commit();
    assertEquals(4, mapper().count());
  }

  @Test
  public void list() throws Exception {
    List<Dataset> ds = Lists.newArrayList();
    ds.add(mapper().get(1));
    ds.add(mapper().get(2));
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());

    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
      }
      // dont compare created stamps
      d.setCreated(null);
    }
    commit();

    // get first page
    Page p = new Page(0,4);

    List<Dataset> res = removeCreated(mapper().list(p));
    assertEquals(4, res.size());
    assertEquals(Lists.partition(ds, 4).get(0), res);

    // next page
    p.next();
    res = removeCreated(mapper().list(p));
    assertEquals(3, res.size());
    List<Dataset> l2 = Lists.partition(ds, 4).get(1);
    assertEquals(l2, res);
  }

  private List<Dataset> removeCreated(List<Dataset> ds) {
    for (Dataset d : ds) {
      // dont compare created stamps
      d.setCreated(null);
    }
    return ds;
  }
}
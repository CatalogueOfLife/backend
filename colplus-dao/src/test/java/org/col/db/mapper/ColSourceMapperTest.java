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
    ColSource d1 = create(DATASET11.getKey());
    mapper().create(d1);
    
    commit();
    
    ColSource d2 = mapper().getEditable(d1.getKey());
    // remove newly set property
    d2.setCreated(null);
    
    assertEquals(d1, d2);
  }
  
  @Test
  public void delete() throws Exception {
    ColSource d1 = create(DATASET11.getKey());
    mapper().create(d1);
    
    commit();
    
    // not deleted yet
    ColSource d = mapper().get(d1.getKey());
    assertNotNull(d.getCreated());
    
    // physically delete
    mapper().delete(d1.getKey());
    assertNull(mapper().get(d1.getKey()));
  }
  
  public static ColSource create(int datasetKey) {
    ColSource d = new ColSource();
    d.setDatasetKey(datasetKey);
    d.setCoverage(DatasetType.GLOBAL);
    d.setTitle(RandomUtils.randomString(80));
    d.setDescription(RandomUtils.randomString(500));
    for (int i = 0; i < 8; i++) {
      d.getAuthorsAndEditors().add(RandomUtils.randomString(100));
    }
    d.setContact("Hans Peter");
    d.setReleased(LocalDate.now());
    d.setVersion("v123");
    d.setWebsite(URI.create("https://www.gbif.org/dataset/13"));
    d.getOrganisations().add("my org");
    d.getOrganisations().add("your org");
    d.setNamesCount(23456);
    d.setAlias("ILDIS");
    d.setNotes("I hate my work");
    d.setConfidence(2);
    d.setCompleteness(88);
    return d;
  }
  
}
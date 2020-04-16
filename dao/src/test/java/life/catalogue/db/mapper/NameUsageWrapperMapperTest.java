package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Datasets;
import org.apache.ibatis.cursor.Cursor;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static life.catalogue.api.TestEntityGenerator.TAXON2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class NameUsageWrapperMapperTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTest() {
    super(NameUsageWrapperMapper.class);
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void processDatasetBareNames() throws Exception {
    Cursor<NameUsageWrapper> c = mapper().processDatasetBareNames(NAME4.getDatasetKey(), null);
    c.forEach(obj -> {
        counter.incrementAndGet();
        assertNotNull(obj);
        assertNotNull(obj.getUsage());
        assertNotNull(obj.getUsage().getName());
    });
    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void testGetComplete() throws Exception {
    DatasetMapper dm = mapper(DatasetMapper.class);
    Dataset d = dm.get(Datasets.DRAFT_COL);
    d.setGbifPublisherKey(UUID.randomUUID());
    dm.update(d);

    Taxon t = TestEntityGenerator.newTaxon(Datasets.DRAFT_COL);
    NameMapper nm = mapper(NameMapper.class);
    nm.create(t.getName());

    TaxonMapper tm = mapper(TaxonMapper.class);
    tm.create(t);

    SectorMapper sm = mapper(SectorMapper.class);
    Sector s = new Sector();
    s.setSubjectDatasetKey(TAXON2.getDatasetKey());
    s.setSubject(TAXON2.toSimpleName());
    s.setDatasetKey(d.getKey());
    s.setTarget(t.toSimpleName());
    s.applyUser(TestEntityGenerator.USER_USER);
    sm.create(s);

    t.setSectorKey(s.getId());
    tm.update(t);

    commit();

    NameUsageWrapper w = mapper().get(Datasets.DRAFT_COL, t.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    Taxon wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    assertEquals(s.getId(), wt.getSectorKey());
    assertEquals(TAXON2.getDatasetKey(), w.getSectorDatasetKey());
    assertEquals(d.getGbifPublisherKey(), w.getPublisherKey());

    w = mapper().getWithoutClassification(Datasets.DRAFT_COL, t.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    assertEquals(s.getId(), wt.getSectorKey());
    assertEquals(TAXON2.getDatasetKey(), w.getSectorDatasetKey());
    assertEquals(d.getGbifPublisherKey(), w.getPublisherKey());
  }
}

package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.Datasets;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Sector metadata is a sparse Dataset keyed by a sector. See issue #1273.
 */
public class SectorMetadataMapperTest extends MapperTestBase<SectorMetadataMapper> {

  public SectorMetadataMapperTest() {
    super(SectorMetadataMapper.class);
  }

  /**
   * sector_metadata is FK'd to sector, so every test needs a real sector first.
   */
  private Sector createSector() {
    Sector s = SectorMapperTest.create();
    mapper(SectorMapper.class).create(s);
    commit();
    return s;
  }

  /**
   * A minimal sector metadata document, reusable from other mapper tests.
   */
  public static Dataset metadata(String title) {
    Dataset d = new Dataset();
    d.setTitle(title);
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    return d;
  }

  private DSID<Integer> key(Sector s) {
    return DSID.of(s.getDatasetKey(), s.getId());
  }

  /**
   * Everything a sector can say about itself has to survive a round trip: agent composites, agent
   * arrays, the hstore urlFormatter, the fuzzy date and the citation list in its own table.
   */
  @Test
  public void roundtripCrud() throws Exception {
    Sector s = createSector();
    Dataset d1 = removeNonPatchProps(DatasetMapperTest.populate(new Dataset()));
    // a sector is not a dataset: these are meaningless here and are not columns we write
    d1.setVersionDoi(null);
    d1.setKey(null);
    // license is a real column but is excluded from PATCH_PROPS ("required"), so removeNonPatchProps
    // just cleared it. Put it back: the resolver has to apply it explicitly and the column must work.
    d1.setLicense(life.catalogue.api.vocab.License.CC_BY);
    TestEntityGenerator.setUserDate(d1);

    mapper().create(key(s), d1);
    commit();

    Dataset d2 = mapper().get(key(s));
    assertNotNull(d2);
    // the row describes a sector, so it must not pretend to be a dataset
    assertNull("Dataset.key must stay null - this row describes a sector", d2.getKey());
    assertEquals(d1.getTitle(), d2.getTitle());
    assertEquals(d1.getDoi(), d2.getDoi());
    assertEquals(d1.getCreator(), d2.getCreator());
    assertEquals(d1.getEditor(), d2.getEditor());
    assertEquals(d1.getContact(), d2.getContact());
    assertEquals(d1.getPublisher(), d2.getPublisher());
    assertEquals(d1.getUrlFormatter(), d2.getUrlFormatter());
    assertEquals(d1.getIssued(), d2.getIssued());
    assertEquals(d1.getLicense(), d2.getLicense());
    assertEquals(d1.getKeyword(), d2.getKeyword());
    assertEquals(d1.getIdentifier(), d2.getIdentifier());
    assertEquals(d1.getConfidence(), d2.getConfidence());
    assertEquals(d1.getGeographicScope(), d2.getGeographicScope());

    // update
    d1.setTitle("World Porifera Database");
    mapper().update(key(s), d1);
    commit();
    assertEquals("World Porifera Database", mapper().get(key(s)).getTitle());

    assertEquals(List.of(s.getId()), mapper().listSectorIds(s.getDatasetKey()));

    mapper().delete(key(s));
    commit();
    assertNull(mapper().get(key(s)));
    assertTrue(mapper().listSectorIds(s.getDatasetKey()).isEmpty());
  }

  /**
   * Citations live in sector_citation and are read back through a two column nested select.
   */
  @Test
  public void citations() throws Exception {
    Sector s = createSector();
    Dataset d = new Dataset();
    d.setTitle("MolluscaBase");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    mapper().create(key(s), d);

    Citation c = new Citation();
    c.setId("mb");
    c.setTitle("MolluscaBase eds. (2026)");
    c.setAuthor(List.of(TestEntityGenerator.newCslName()));
    mapper(CitationMapper.class).createSector(s.getDatasetKey(), s.getId(), c);
    commit();

    Dataset d2 = mapper().get(key(s));
    assertEquals(1, d2.getSource().size());
    assertEquals("mb", d2.getSource().get(0).getId());
    assertEquals("MolluscaBase eds. (2026)", d2.getSource().get(0).getTitle());
    assertEquals(c.getAuthor(), d2.getSource().get(0).getAuthor());

    // a sector without citations must come back with an empty list, never with another sector's
    Sector s2 = createSector();
    Dataset e = new Dataset();
    e.setTitle("no citations");
    e.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    e.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    mapper().create(key(s2), e);
    commit();
    assertTrue(mapper().get(key(s2)).getSource().isEmpty());
  }

  /**
   * The one ON DELETE CASCADE in the schema. Without it deleteOrphans in ProjectRelease.finalWork and
   * DatasetDao.deleteKeptReleaseData both raise a FK violation.
   */
  @Test
  public void cascadeOnSectorDelete() throws Exception {
    Sector s = createSector();
    Dataset d = new Dataset();
    d.setTitle("World Porifera Database");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    mapper().create(key(s), d);
    Citation c = new Citation();
    c.setId("wpd");
    c.setTitle("de Voogd et al.");
    mapper(CitationMapper.class).createSector(s.getDatasetKey(), s.getId(), c);
    commit();
    assertNotNull(mapper().get(key(s)));

    mapper(SectorMapper.class).delete(key(s));
    commit();

    assertNull(mapper().get(key(s)));
    assertTrue(mapper(CitationMapper.class).listSector(s.getDatasetKey(), s.getId()).isEmpty());
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

  /**
   * Clear everything Dataset.PATCH_PROPS does not cover, so the round trip only asserts what we store.
   */
  private Dataset removeNonPatchProps(Dataset d) throws Exception {
    for (PropertyDescriptor prop : Introspector.getBeanInfo(Dataset.class, Object.class).getPropertyDescriptors()) {
      if (prop.getWriteMethod() == null) continue;
      if (!Dataset.PATCH_PROPS.contains(prop)) {
        if (prop.getWriteMethod().getParameterTypes().length > 1) continue;
        if (prop.getWriteMethod().getParameterTypes()[0].isPrimitive()) {
          prop.getWriteMethod().invoke(d, false);
        } else {
          prop.getWriteMethod().invoke(d, (Object) null);
        }
      }
    }
    return d;
  }
}

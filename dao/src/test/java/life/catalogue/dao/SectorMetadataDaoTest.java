package life.catalogue.dao;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.License;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The resolution chain of issue #1273. The fish test data gives us project 100 with sector 1 pointing
 * at the external dataset 101 (FishBase), which is exactly the shape this feature is about.
 */
public class SectorMetadataDaoTest extends DaoTestBase {
  static final int PROJECT = 100;
  static final int SOURCE = 101;
  static final int SECTOR = 1;
  static final int SRC_SECTOR = 1044;

  public SectorMetadataDaoTest() {
    super(TestDataRule.fish());
  }

  private SectorMetadataDao dao() {
    return new SectorMetadataDao(factory(), new DatasetSourceDao(factory()));
  }

  private Citation citation(String id, String title) {
    Citation c = new Citation();
    c.setId(id);
    c.setTitle(title);
    return c;
  }

  private Dataset patch(String title) {
    Dataset d = new Dataset();
    d.setTitle(title);
    d.setCreatedBy(100);
    d.setModifiedBy(100);
    return d;
  }

  /**
   * The common case by a wide margin: a sector says nothing, so it renders its source dataset.
   */
  @Test
  public void inheritsWhenSilent() {
    Dataset d = dao().resolve(DSID.of(PROJECT, SECTOR));
    assertNotNull(d);
    assertEquals("FishBase", d.getTitle());
  }

  @Test
  public void unknownSectorResolvesToNull() {
    assertNull(dao().resolve(DSID.of(PROJECT, 999)));
  }

  /**
   * publisher declared, then the editor's override on top, then back down again as each is removed.
   */
  @Test
  public void layering() {
    var dao = dao();

    // the source declares a part of its own data
    Sector src = new Sector();
    src.setDatasetKey(SOURCE);
    src.setId(SRC_SECTOR);
    src.setMode(Sector.Mode.SOURCE);
    src.setCreatedBy(100);
    src.setModifiedBy(100);
    mapper(SectorMapper.class).createWithID(src);
    commit(); // the dao opens its own session and would not see an uncommitted sector

    Dataset declared = patch("Eschmeyer's Catalog of Fishes");
    declared.setLicense(License.CC0);
    declared.setAlias("ECoF");
    declared.setSource(List.of(citation("ecof", "Fricke, Eschmeyer & Van der Laan")));
    dao.putPatch(DSID.of(SOURCE, SRC_SECTOR), declared, 100);

    // the project sector absorbs it
    Sector s = mapper(SectorMapper.class).get(DSID.of(PROJECT, SECTOR));
    s.setSubjectSectorId(SRC_SECTOR);
    mapper(SectorMapper.class).update(s);
    commit();

    Dataset d = dao.resolve(DSID.of(PROJECT, SECTOR));
    assertEquals("Eschmeyer's Catalog of Fishes", d.getTitle());
    assertEquals("ECoF", d.getAlias());
    // license is excluded from PATCH_PROPS, so this only works because the dao applies it explicitly
    assertEquals(License.CC0, d.getLicense());
    assertEquals(1, d.getSource().size());
    assertEquals("ecof", d.getSource().get(0).getId());

    // the editor overrides just the title. Everything else must still come from the declared layer.
    dao.putPatch(DSID.of(PROJECT, SECTOR), patch("Catalog of Fishes, CoL edition"), 100);

    d = dao.resolve(DSID.of(PROJECT, SECTOR));
    assertEquals("Catalog of Fishes, CoL edition", d.getTitle());
    assertEquals("ECoF", d.getAlias());
    assertEquals(License.CC0, d.getLicense());
    // the override carries no citations, so it inherits rather than wipes them
    assertEquals(1, d.getSource().size());

    // drop the override and we are back to what the publisher declared
    dao.deletePatch(DSID.of(PROJECT, SECTOR));
    d = dao.resolve(DSID.of(PROJECT, SECTOR));
    assertEquals("Eschmeyer's Catalog of Fishes", d.getTitle());

    // drop the link and we are back to the plain source dataset
    s.setSubjectSectorId(null);
    mapper(SectorMapper.class).update(s);
    commit();
    d = dao.resolve(DSID.of(PROJECT, SECTOR));
    assertEquals("FishBase", d.getTitle());
  }

  /**
   * What a release freezes: the two sector level layers flattened into one sparse document, so a later
   * re-import of the source cannot change what an already published release renders.
   */
  @Test
  public void mergedDelta() {
    var dao = dao();
    Sector src = new Sector();
    src.setDatasetKey(SOURCE);
    src.setId(SRC_SECTOR);
    src.setMode(Sector.Mode.SOURCE);
    src.setCreatedBy(100);
    src.setModifiedBy(100);
    mapper(SectorMapper.class).createWithID(src);
    commit();

    Dataset declared = patch("Eschmeyer's Catalog of Fishes");
    declared.setAlias("ECoF");
    dao.putPatch(DSID.of(SOURCE, SRC_SECTOR), declared, 100);

    Sector s = mapper(SectorMapper.class).get(DSID.of(PROJECT, SECTOR));
    s.setSubjectSectorId(SRC_SECTOR);
    mapper(SectorMapper.class).update(s);
    commit();
    dao.putPatch(DSID.of(PROJECT, SECTOR), patch("Catalog of Fishes, CoL edition"), 100);

    Dataset delta = dao.mergedDelta(s, session());
    assertNotNull(delta);
    assertEquals("Catalog of Fishes, CoL edition", delta.getTitle());
    assertEquals("ECoF", delta.getAlias());
    // still a sparse document - it must not have absorbed the source dataset itself
    assertNull(delta.getDescription());

    // a sector with nothing of its own has nothing to freeze
    Sector bare = new Sector();
    bare.setDatasetKey(PROJECT);
    bare.setId(4242);
    bare.setSubjectDatasetKey(SOURCE);
    bare.setMode(Sector.Mode.ATTACH);
    bare.setCreatedBy(100);
    bare.setModifiedBy(100);
    mapper(SectorMapper.class).createWithID(bare);
    commit();
    assertNull(dao.mergedDelta(bare, session()));
  }
}

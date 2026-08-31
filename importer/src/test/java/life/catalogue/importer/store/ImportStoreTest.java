package life.catalogue.importer.store;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.importer.store.model.UsageData;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import com.google.common.io.Files;

import static life.catalogue.importer.store.PreviousIdsTest.u;
import static org.junit.Assert.*;


public class ImportStoreTest {
  private int datasetKey;
  private final static NormalizerConfig cfg = new NormalizerConfig();
  private static ImportStoreFactory importStoreFactory;

  ImportStore db;
  
  @BeforeClass
  public static void initRepo() {
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    importStoreFactory = new ImportStoreFactory(cfg);
  }
  
  @Before
  public void init() throws IOException {
    datasetKey = RandomUtils.randomInt();
    System.out.println("Use datasetKey "+datasetKey);
    db = importStoreFactory.create(datasetKey, 1);
  }
  
  @After
  public void destroy() {
    if (db != null) {
      db.close();
    }
  }
  
  @AfterClass
  public static void destroyRepo() throws Exception {
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  @Test
  public void updateTaxon() throws Exception {
    var u = taxon("id1");
    db.createNameAndUsage(u);

    VerbatimRecord tr = new VerbatimRecord(123, "bla.txt", GbifTerm.VernacularName);
    tr.setType(AcefTerm.Distribution);
    tr.put(AcefTerm.DistributionElement, "Asia");

    var u2 = db.usages().objByID("id1");
    u2.setVerbatimKey(tr.getId());
    db.usages().update(u2);

    var u3 = db.usages().objByID("id1");
    assertEquals(u2, u3);
  }
  
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/389
   */
  @Test
  public void createCyrillicRef() throws Exception {
    // this citation has a nearly invisible cyrillic o that cannot be folded into ASCII
    Reference r = TestEntityGenerator.newReference();
    r.setCitation("Contribuciоnes al conocimiento de la flora del Gondwana Superior en la Argentina. XXXIII \"Ginkgoales\" de los Estratos de Potrerillos en la Precordillera de Mendoza.");
    db.references().create(r);

    r = TestEntityGenerator.newReference();
    r.setCitation("Mandarin:哦诶艾诶艾哦屁杰诶  Japanese:ｪｺｻｪ ｷｼｪｩｪ ｺｪｹ ｻｼ ｴｮｨｱ  Other: ወለi էዠለi   mබƖ tƕබƖ   ꀪꋬꊛ ꓄ꈚꋬꊛ");
    db.references().create(r);
  }

  /**
   * Reopens the store for this test with the given previously generated ids in place.
   */
  private void storeWith(PreviousIds prevIds) {
    db.close();
    db = importStoreFactory.create(datasetKey, 1, prevIds);
  }

  /** an implicit taxon as the normalizer creates it for a denormalised classification, i.e. without any id */
  private static NameUsageData implicitTaxon(Rank rank, String name, String parentId) {
    UsageData t = UsageData.buildTaxon(Origin.DENORMED_CLASSIFICATION, TaxonomicStatus.ACCEPTED);
    t.asTaxon().setParentId(parentId);
    Name n = new Name();
    n.setRank(rank);
    n.setScientificName(name);
    return new NameUsageData(new NameData(n), t);
  }

  /**
   * The ids a previous import generated for the same name come back, name and usage id both, see #1189.
   */
  @Test
  public void reapplyPreviousIds() {
    storeWith(PreviousIds.of(false, u("x3", "x4", Rank.GENUS, "Aster", null, null)));

    var nu = implicitTaxon(Rank.GENUS, "Aster", null);
    assertTrue(db.createNameAndUsage(nu));
    assertEquals("x3", nu.ud.getId());
    assertEquals("x4", nu.nd.getId());
    assertEquals("x4", nu.ud.nameID);
  }

  /**
   * A source record of this import holding the previous id wins - the implicit record gets a new one.
   */
  @Test
  public void previousIdTakenBySource() {
    storeWith(PreviousIds.of(false, u("x3", "x4", Rank.GENUS, "Aster", null, null)));

    var src = taxon("x3"); // a source record occupying both the usage and the name id
    assertTrue(db.createNameAndUsage(src));

    var nu = implicitTaxon(Rank.GENUS, "Aster", null);
    assertTrue(db.createNameAndUsage(nu));
    assertNotEquals("x3", nu.ud.getId());
    assertNotEquals("x4", nu.nd.getId());
  }

  /**
   * A brand new record must not take an id a previous import generated, or the record that record belongs to
   * would find its id occupied and the ids would keep churning. This is what CRUDStore reserves for.
   */
  @Test
  public void newRecordDoesNotStealAReservedId() {
    // ~3 is the very first id the generator hands out, and also the id a previous record holds
    storeWith(PreviousIds.of(false, u("~3", "~4", Rank.GENUS, "Betula", null, null)));

    var fresh = implicitTaxon(Rank.GENUS, "Aster", null);
    assertTrue(db.createNameAndUsage(fresh));
    assertNotEquals("~3", fresh.ud.getId());
    assertNotEquals("~3", fresh.nd.getId());
    assertNotEquals("~4", fresh.ud.getId());
    assertNotEquals("~4", fresh.nd.getId());

    // and the record it was reserved for still gets it
    var betula = implicitTaxon(Rank.GENUS, "Betula", null);
    assertTrue(db.createNameAndUsage(betula));
    assertEquals("~3", betula.ud.getId());
    assertEquals("~4", betula.nd.getId());
  }

  /**
   * A TextTree line number that another record reclaimed by name is replaced by a generated id.
   */
  @Test
  public void generatedPlaceholderGivesWay() {
    storeWith(PreviousIds.of(true, u("5", "5", Rank.GENUS, "Betula", null, null)));

    var nu = implicitTaxon(Rank.GENUS, "Aster", null);
    nu.ud.setId("5"); // the line number this record happens to sit on now
    nu.nd.setId("5");
    assertTrue(db.createNameAndUsage(nu, true));
    assertNotEquals("5", nu.ud.getId());
    assertNotEquals("5", nu.nd.getId());
  }

  /**
   * A record the source identifies itself never consumes a previous id.
   */
  @Test
  public void sourceRecordsKeepTheirOwnId() {
    storeWith(PreviousIds.of(false, u("x3", "x4", Rank.GENUS, "Aster", null, null)));

    var src = implicitTaxon(Rank.GENUS, "Aster", null);
    src.ud.usage.setOrigin(Origin.SOURCE);
    src.ud.setId("s1");
    assertTrue(db.createNameAndUsage(src));
    assertEquals("s1", src.ud.getId());

    // the candidate is still there for the implicit record
    var nu = implicitTaxon(Rank.GENUS, "Aster", null);
    assertTrue(db.createNameAndUsage(nu));
    assertEquals("x3", nu.ud.getId());
  }

  public static NameUsageData taxon(String id) {
    UsageData t = UsageData.buildTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
    NameData n = new NameData(RandomUtils.randomName());
    t.setId(id);
    return new NameUsageData(n,t);
  }
}
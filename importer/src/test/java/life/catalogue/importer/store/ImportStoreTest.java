package life.catalogue.importer.store;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
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

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import com.google.common.io.Files;

import static org.junit.Assert.assertEquals;


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

  public static NameUsageData taxon(String id) {
    UsageData t = UsageData.buildTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
    NameData n = new NameData(RandomUtils.randomName());
    t.setId(id);
    return new NameUsageData(n,t);
  }
}
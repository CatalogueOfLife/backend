package life.catalogue.importer.store;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.store.model.UsageData;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import com.google.common.io.Files;


public class NeoDbTest {
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
  
  static class BatchProcException extends RuntimeException {
    BatchProcException(String message) {
      super(message);
    }
  }

  @Test
  public void updateTaxon() throws Exception {
    UsageData u = taxon("id1");
    db.createNameAndUsage(u);

    VerbatimRecord tr = new VerbatimRecord(123, "bla.txt", GbifTerm.VernacularName);
    tr.setType(AcefTerm.Distribution);
    tr.put(AcefTerm.DistributionElement, "Asia");

    u = db.usages().objByID("id1");
    db.usages().update(u);

    u = db.usages().objByID("id1");
    //assertEquals(1, t.verbatim.getExtensionRecords(AcefTerm.Distribution).size());
    //assertEquals(tr, t.verbatim.getExtensionRecords(AcefTerm.Distribution).getUsage(0));
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

  public static UsageData taxon(String id) {
    UsageData t = UsageData.createTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
    t.usage.setName(RandomUtils.randomName());
    t.setId(id);
    return t;
  }
}
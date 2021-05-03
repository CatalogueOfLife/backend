package life.catalogue.exporter;

import com.google.common.io.Files;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.db.LookupTables;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageService;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AcExporterTest extends ExporterTest {
  
  WsServerConfig cfg;
  File arch;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.draftWithSectors();

  @Before
  public void initCfg()  {
    cfg = new WsServerConfig();
    cfg.db = PgSetupRule.getCfg();
    cfg.exportDir = Files.createTempDir();
    cfg.normalizer.scratchDir  = Files.createTempDir();
    cfg.img.repo = cfg.normalizer.scratchDir.toPath();
  }
  
  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.exportDir);
    FileUtils.deleteQuietly(cfg.normalizer.scratchDir);
    if (arch != null) {
      System.out.println(arch.getAbsolutePath());
      FileUtils.deleteQuietly(arch);
    }
  }
  
  @Test
  public void export() throws Exception {
    AcefExporterJob exp = new AcefExporterJob(new ExportRequest(Datasets.COL, DataFormat.ACEF), Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    // prepare metadata
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {

      LookupTables.recreateTables(session.getConnection());

      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(Datasets.COL);
      d.setEditors(Person.parse(List.of("Röskøv Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
      d.setOrganisations(Organisation.parse("Species 2000", "ITIS Catalogue of Life"));
      d.setReleased(null);
      dm.update(d);
    }
    
    exp.run();

    arch = exp.getArchive();
    System.out.println("LOGS:\n");

    // test decompressed archive
    File check = new File(cfg.normalizer.scratchDir, "archiveCheck");
    CompressionUtil.unzipFile(check, arch);

    // check common names file
    File cnf = new File(check, "common_names.csv");
    String content = FileUtils.readFileToString(cnf, StandardCharsets.UTF_8);
    System.out.println(content);
    assertEquals("record_id\tname_code\tcommon_name\ttransliteration\tlanguage\tcountry\tarea\treference_id\tdatabase_id\tis_infraspecies\treference_code\n" +
            "1047\t1\tTännø\t\\N\tdeu\t\\N\t\\N\t1\t500\t\\N\tr1\n" +
            "1048\t1\tFír\t\\N\teng\t\\N\t\\N\t2\t500\t\\N\tr2\n" +
            "1049\t2\tWeiß-Tanne\t\\N\tdeu\t\\N\t\\N\t3\t500\t\\N\tr3\n" +
            "1050\t2\tEuropean silver fir\t\\N\teng\t\\N\t\\N\t\\N\t500\t\\N\t\\N", content.trim());

    content = FileUtils.readFileToString(new File(check, "credits.ini"), StandardCharsets.UTF_8);
    System.out.println(content);
  }

  @Test
  public void exportNonManaged() throws Exception {
    // run different test data rule
    MybatisTestUtils.populateTestData(TestDataRule.APPLE, false);
    final int key = TestDataRule.APPLE.key;

    // prepare metadata
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(key);
      d.setEditors(Person.parse(List.of("Röskøv Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
      d.setOrganisations(Organisation.parse("Species 2000", "ITIS Catalogue of Life"));
      d.setReleased(null);
      d.setOrigin(DatasetOrigin.MANAGED);
      dm.update(d);
    }

    AcefExporterJob exp = new AcefExporterJob(new ExportRequest(key, DataFormat.ACEF), Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    arch = exp.getArchive();
    System.out.println("LOGS:\n");

    // test decompressed archive
    File check = new File(cfg.normalizer.scratchDir, "archiveCheck");
    CompressionUtil.unzipFile(check, arch);

    String credits = FileUtils.readFileToString(new File(check, "credits.ini"), StandardCharsets.UTF_8);
    System.out.println(credits);

  }
}
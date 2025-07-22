package life.catalogue.exporter;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.db.LookupTables;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;

import static org.junit.Assert.assertEquals;

public class AcefExportIT extends ExportTest {
  
  File arch;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @BeforeClass
  public static void setupFirst() throws Exception {
    // we need lookup tables for this mapper
    try (var con = pgSetupRule.connect()) {
      LookupTables.recreateTables(con);
    }
  }

  @Rule
  public TestDataRule testDataRule = TestDataRule.draftWithSectors();

  @After
  public void cleanup2()  {
    if (arch != null) {
      System.out.println("Remove " + arch.getAbsolutePath());
      FileUtils.deleteQuietly(arch);
    }
  }
  
  @Test
  public void export() throws Exception {
    AcefExport exp = new AcefExport(new ExportRequest(Datasets.COL, DataFormat.ACEF), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    // prepare metadata
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {

      LookupTables.recreateTables(session.getConnection());

      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(Datasets.COL);
      d.setEditor(Agent.parse(List.of("Röskøv Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
      d.setContributor(Agent.parse("Species 2000", "ITIS Catalogue of Life"));
      d.setIssued(null);
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
            "1047\t1\tTännø\t\\N\tdeu\t\\N\t\\N\t1\t100\t\\N\tr1\n" +
            "1048\t1\tFír\t\\N\teng\t\\N\t\\N\t2\t100\t\\N\tr2\n" +
            "1049\t2\tWeiß-Tanne\t\\N\tdeu\t\\N\t\\N\t3\t100\t\\N\tr3\n" +
            "1050\t2\tEuropean silver fir\t\\N\teng\t\\N\t\\N\t\\N\t100\t\\N\t\\N", content.trim());
  }

  @Test
  public void exportNonManaged() throws Exception {
    // run different test data rule
    MybatisTestUtils.replaceTestData(TestDataRule.APPLE);
    final int key = TestDataRule.APPLE.key;

    // prepare metadata
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(key);
      d.setEditor(Agent.parse(List.of("Röskøv Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
      d.setContributor(Agent.parse("Species 2000", "ITIS Catalogue of Life"));
      d.setIssued(null);
      d.setOrigin(DatasetOrigin.PROJECT);
      dm.update(d);
    }

    AcefExport exp = new AcefExport(new ExportRequest(key, DataFormat.ACEF), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    arch = exp.getArchive();
    System.out.println("LOGS:\n");

    // test decompressed archive
    File check = new File(cfg.normalizer.scratchDir, "archiveCheck");
    CompressionUtil.unzipFile(check, arch);
  }
}
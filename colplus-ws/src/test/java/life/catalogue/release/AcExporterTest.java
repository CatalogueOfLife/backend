package life.catalogue.release;

import com.google.common.io.Files;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TestDataRule;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AcExporterTest {
  
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
    cfg.downloadDir = Files.createTempDir();
    cfg.normalizer.scratchDir  = Files.createTempDir();
  }
  
  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.downloadDir);
    FileUtils.deleteQuietly(cfg.normalizer.scratchDir);
    if (arch != null) {
      System.out.println(arch.getAbsolutePath());
      FileUtils.deleteQuietly(arch);
    }
  }
  
  @Test
  public void export() throws Exception {
    AcExporter exp = new AcExporter(cfg, PgSetupRule.getSqlSessionFactory());
    // prepare metadata
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(Datasets.DRAFT_COL);
      d.setAuthorsAndEditors(List.of("Röskøv Y.", "Ower G.", "Orrell T.", "Nicolson D."));
      d.setOrganisations(List.of("Species 2000", "ITIS Catalogue of Life"));
      d.setReleased(null);
      dm.update(d);
    }
    
    Logger lg = new Logger();
    arch = exp.export(Datasets.DRAFT_COL, lg);
    System.out.println("LOGS:\n");
    System.out.println(lg.toString());

    // test decompressed archive
    File check = new File(cfg.normalizer.scratchDir, "archiveCheck");
    CompressionUtil.unzipFile(check, arch);

    // check common names file
    File cnf = new File(check, "common_names.csv");
    String content = FileUtils.readFileToString(cnf, StandardCharsets.UTF_8);
    System.out.println(content);
    assertEquals("record_id\tname_code\tcommon_name\ttransliteration\tlanguage\tcountry\tarea\treference_id\tdatabase_id\tis_infraspecies\treference_code\n" +
            "1047\t1\tTännø\tNULL\tdeu\tNULL\tNULL\t1\t500\tNULL\tr1\n" +
            "1048\t1\tFír\tNULL\teng\tNULL\tNULL\t2\t500\tNULL\tr2\n" +
            "1049\t2\tWeiß-Tanne\tNULL\tdeu\tNULL\tNULL\t3\t500\tNULL\tr3\n" +
            "1050\t2\tEuropean silver fir\tNULL\teng\tNULL\tNULL\tNULL\t500\tNULL\tNULL", content.trim());

    content = FileUtils.readFileToString(new File(check, "credits.ini"), StandardCharsets.UTF_8);
    System.out.println(content);

  }
  
}
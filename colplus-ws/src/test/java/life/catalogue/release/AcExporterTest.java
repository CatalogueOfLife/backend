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
      d.setCitation("Röskøv Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life");
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
    assertEquals("record_id,name_code,common_name,transliteration,language,country,area,reference_id,database_id,is_infraspecies,reference_code\n" +
            "1047,1,Tännø,\\N,deu,\\N,\\N,1,500,\\N,r1\n" +
            "1048,1,Fír,\\N,eng,\\N,\\N,2,500,\\N,r2\n" +
            "1049,2,Weiß-Tanne,\\N,deu,\\N,\\N,3,500,\\N,r3\n" +
            "1050,2,European silver fir,\\N,eng,\\N,\\N,\\N,500,\\N,\\N", content.trim());
  }
  
}
package org.col.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.api.vocab.Datasets;
import org.col.WsServerConfig;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.junit.*;

import static org.junit.Assert.assertEquals;

public class AcExporterTest {
  
  WsServerConfig cfg;
  File arch;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
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
      FileUtils.deleteQuietly(arch);
    }
  }
  
  @Test
  public void export() throws Exception {
    MybatisTestUtils.populateDraftTree(testDataRule.getSqlSession());
    AcExporter exp = new AcExporter(cfg);
    arch = exp.export(Datasets.DRAFT_COL);
  }
  
  @Test
  public void exportConcurrently() throws Exception {
    MybatisTestUtils.populateDraftTree(testDataRule.getSqlSession());
  
    final AtomicInteger exCounter = new AtomicInteger(0);
    
    Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread th, Throwable ex) {
        exCounter.incrementAndGet();
        System.out.println("Uncaught exception: " + ex);
      }
    };
    
    Thread t1 = new Thread(new ExporterRunnable(cfg));
    t1.setUncaughtExceptionHandler(h);
  
    Thread t2 = new Thread(new ExporterRunnable(cfg));
    t2.setUncaughtExceptionHandler(h);

    t1.start();
    t2.start();
    t1.join();
    t2.join();
    
    assertEquals(1, exCounter.get());
  }
  
  static class ExporterRunnable implements Runnable {
    final AcExporter exp;
  
    ExporterRunnable(WsServerConfig cfg) {
      this.exp = new AcExporter(cfg);
    }
  
    @Override
    public void run() {
      try {
        File arch = exp.export(Datasets.DRAFT_COL);
        System.out.println("Export done: " + arch.getAbsolutePath());
      } catch (IOException | SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
}
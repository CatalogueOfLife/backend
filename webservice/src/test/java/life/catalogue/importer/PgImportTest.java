package life.catalogue.importer;

import com.google.common.collect.Lists;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class PgImportTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  AtomicInteger cnt = new AtomicInteger(0);
  
  static class PartitionJob implements Callable<Boolean> {
    final int datasetKey;
    
    PartitionJob(int datasetKey) {
      this.datasetKey = datasetKey;
    }
    
    @Override
    public Boolean call() throws Exception {
      System.out.println("START " + datasetKey);
      System.out.println("PARTITION " + datasetKey);
      Partitioner.partition(PgSetupRule.getSqlSessionFactory(), datasetKey, DatasetOrigin.MANAGED);

      System.out.println("INDEX & ATTACH " + datasetKey);
      Partitioner.attach(PgSetupRule.getSqlSessionFactory(), datasetKey, DatasetOrigin.MANAGED);
      System.out.println("FINISHED " + datasetKey);
      return true;
    }
  }
  
  @Test
  public void testConcurrentPartitioning() throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      testConcurrentPartitioningOnce(exec);
      // run same dataset keys again so we have to delete the previous ones
      testConcurrentPartitioningOnce(exec);
      exec.shutdown();
      
    } finally {
      ExecutorUtils.shutdown(exec, 10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void duplicateAlias() throws Exception {
    Dataset d = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("first"));
    d.setAlias("first");

    Dataset d2 = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("second"));
    d2.setAlias("second");
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.create(d);
      dm.create(d2);
    }

    DatasetWithSettings ds = new DatasetWithSettings(d2, new DatasetSettings());
    d2.setAlias(d.getAlias());
    PgImport imp = new PgImport(1, ds, null, PgSetupRule.getSqlSessionFactory(), new ImporterConfig(), null);
    imp.updateMetadata();
  }

  private void testConcurrentPartitioningOnce(ExecutorService exec) throws Exception {
    System.out.println("\n\nSTART SEQUENTIAL PASS " + cnt.incrementAndGet());
    System.out.println("\n");
    List<Future<Boolean>> tasks = Lists.newArrayList();
    for (int k = 3; k < 10; k++) {
      tasks.add(exec.submit(new PartitionJob(k)));
    }
    for (Future<Boolean> f : tasks) {
      assertTrue(f.get());
    }
    System.out.println("\n\nEND SEQUENTIAL PASS " + cnt.get());
    System.out.println("\n");
  }
  
}

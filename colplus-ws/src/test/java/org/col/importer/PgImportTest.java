package org.col.importer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Lists;
import org.col.common.concurrent.ExecutorUtils;
import org.col.dao.Partitioner;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

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
      Partitioner.partition(PgSetupRule.getSqlSessionFactory(), datasetKey);

      System.out.println("INDEX & ATTACH " + datasetKey);
      Partitioner.indexAndAttach(PgSetupRule.getSqlSessionFactory(), datasetKey);
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
      ExecutorUtils.shutdown(exec);
    }
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

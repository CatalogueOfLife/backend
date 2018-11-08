package org.col.admin.importer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.common.concurrent.ExecutorUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetPartitionMapper;
import org.col.db.mapper.InitMybatisRule;
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
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();
  
  static class PartitionJob implements Callable<Boolean> {
    final int datasetKey;
    
    PartitionJob(int datasetKey) {
      this.datasetKey = datasetKey;
    }
    
    @Override
    public Boolean call() throws Exception {
      try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
        PgImport.partition(session, datasetKey);
        
        DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
        mapper.buildIndices(datasetKey);
        mapper.attach(datasetKey);
        session.commit();
      }
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
    List<Future<Boolean>> tasks = Lists.newArrayList();
    for (int k = 3; k < 25; k++) {
      tasks.add(exec.submit(new PartitionJob(k)));
    }
    for (Future<Boolean> f : tasks) {
      assertTrue(f.get());
    }
  }
  
}

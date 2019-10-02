package org.col.importer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.dropwizard.lifecycle.Managed;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.common.concurrent.ExecutorUtils;
import org.col.config.ImporterConfig;
import org.col.api.model.Dataset;
import org.col.api.vocab.Users;
import org.col.common.util.LoggingUtils;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * A scheduler for new import jobs that runs continuously in the background
 * and submits new import jobs to the ImportManager if it is idle.
 * <p>
 * New jobs are selected by priority according to the following criteria:
 * <p>
 * - never imported datasets first
 * - the datasets configured indexing frequency
 */
public class ContinuousImporter implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ContinuousImporter.class);
  private static final String THREAD_NAME = "continuous-importer";
  private static final int WAIT_TIME_IN_HOURS = 1;
  
  private Thread thread;
  private ImportManager manager;
  private ImporterConfig cfg;
  private SqlSessionFactory factory;
  private ContinousImporterJob job;
  
  public ContinuousImporter(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.manager = manager;
    this.factory = factory;
  }
  
  static class ContinousImporterJob implements Runnable {
    private final SqlSessionFactory factory;
    private final ImportManager manager;
    private final ImporterConfig cfg;
    private volatile boolean running = true;
    
    public ContinousImporterJob(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
      this.manager = manager;
      this.factory = factory;
      this.cfg = cfg;
      if (cfg.maxQueue < cfg.continousImportBatchSize) {
        LOG.warn("Importer queue is shorter ({}) than the batch size ({}) to submit. Reduce batches to half the queue size!", cfg.maxQueue, cfg.continousImportBatchSize);
        cfg.continousImportBatchSize = (cfg.maxQueue / 2);
      }
    }
    
    public void terminate() {
      running = false;
    }
    
    @Override
    public void run() {
      MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());
      
      while (running) {
        try {
          while (manager.queueSize() > cfg.continousImportMinSize) {
            LOG.debug("Importer busy, sleep for {} minutes", cfg.continousImportPolling);
            TimeUnit.MINUTES.sleep(cfg.continousImportPolling);
          }
          List<Dataset> datasets = fetch();
          if (datasets.isEmpty()) {
            LOG.info("No datasets eligable to be imported. Sleep for {} hour", WAIT_TIME_IN_HOURS);
            TimeUnit.HOURS.sleep(WAIT_TIME_IN_HOURS);
            
          } else {
            for (Dataset d : datasets) {
              if (!d.hasDeletedDate()) {
                try {
                  manager.submit(new ImportRequest(d.getKey(), Users.IMPORTER, false, false));
                } catch (IllegalArgumentException e) {
                  // ignore
                }
              }
            }
          }
        } catch (InterruptedException e) {
          LOG.info("Interrupted continuous importing. Stop");
          running = false;
          
        } catch (Exception e) {
          LOG.error("Error scheduling continuous imports. Shutdown continous importer!", e);
          running = false;
        }
      }
      MDC.remove(LoggingUtils.MDC_KEY_TASK);
    }
    
    /**
     * Find the next batch of datasets eligable for importing
     */
    private List<Dataset> fetch() {
      // check never crawled datasets first
      List<Dataset> datasets;
      try (SqlSession session = factory.openSession(true)) {
        datasets = session.getMapper(DatasetMapper.class).listNeverImported(cfg.continousImportBatchSize);
        if (datasets.isEmpty()) {
          // now check for eligable datasets based on import frequency
          datasets = session.getMapper(DatasetMapper.class).listToBeImported(cfg.continousImportBatchSize);
        }
      }
      return datasets;
    }
  }
  
  public boolean isActive() {
    return job != null && job.running;
  }
  
  @Override
  public void start() throws Exception {
    if (cfg.continousImportPolling > 0) {
      LOG.info("Enable continuous importing");
      job = new ContinousImporterJob(cfg, manager, factory);
      thread = new Thread(job, THREAD_NAME);
      LOG.info("Start continuous importing with maxQueue={}, polling every {} minutes",
          job.cfg.maxQueue, job.cfg.continousImportPolling
      );
      thread.start();
    
    } else {
      LOG.warn("Continuous importing disabled");
    }
  }
  
  @Override
  public void stop() throws Exception {
    if (job != null) {
      job.terminate();
    }
    if (thread != null) {
      thread.join(ExecutorUtils.MILLIS_TO_DIE);
    }
  }
}

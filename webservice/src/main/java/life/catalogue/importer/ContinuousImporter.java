package life.catalogue.importer;

import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.Managed;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.ContinuousImportConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
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
 *
 * If imports have failed previously, there will be an embargo for 1 week.
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
    private final ContinuousImportConfig cfg;
    private volatile boolean running = true;
    
    public ContinousImporterJob(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
      this.manager = manager;
      this.factory = factory;
      this.cfg = cfg.continuous;
      if (cfg.maxQueue < cfg.batchSize) {
        LOG.warn("Importer queue is shorter ({}) than the batch size ({}) to submit. Reduce batches to half the queue size!", cfg.maxQueue, cfg.batchSize);
        cfg.batchSize = (cfg.maxQueue / 2);
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
          while (!manager.hasStarted()) {
            LOG.debug("Importer not started, sleep for {} minutes", cfg.polling);
            TimeUnit.MINUTES.sleep(cfg.polling);
          }
          while (manager.queueSize() > cfg.threshold) {
            LOG.debug("Importer busy, sleep for {} minutes", cfg.polling);
            TimeUnit.MINUTES.sleep(cfg.polling);
          }
          List<DatasetMapper.DatasetAttempt> datasets = fetch();
          if (datasets.isEmpty()) {
            LOG.debug("No datasets eligable to be imported. Sleep for {} hour", WAIT_TIME_IN_HOURS);
            TimeUnit.HOURS.sleep(WAIT_TIME_IN_HOURS);
            
          } else {
            LOG.info("Trying to schedule {} dataset imports", datasets.size());
            datasets.forEach(this::scheduleImport);
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

    private void scheduleImport(DatasetMapper.DatasetAttempt d) {
      try {
        if (d.isFailed()) {
          LOG.info("Schedule a forced import of dataset {} which failed the last time on {}: {}", d.getKey(), d.getLastImportAttempt(), d.getTitle());
        }
        manager.submit(ImportRequest.external(d.getKey(), Users.IMPORTER, d.isFailed()));
      } catch (IllegalArgumentException e) {
        LOG.warn("Failed to schedule a {}dataset import {}: {}", d.isFailed()? "forced ":"", d.getKey(), d.getTitle(), e);
      }
    }

    /**
     * Find the next batch of datasets eligable for importing
     */
    private List<DatasetMapper.DatasetAttempt> fetch() {
      // check never crawled datasets first
      try (SqlSession session = factory.openSession(true)) {
        List<DatasetMapper.DatasetAttempt> datasets = session.getMapper(DatasetMapper.class).listNeverImported(cfg.batchSize);
        removeRunningImports(datasets);
        if (datasets.isEmpty()) {
          // now check for eligable datasets based on import frequency
          datasets = session.getMapper(DatasetMapper.class).listToBeImported(cfg.defaultFrequency, cfg.batchSize);
          removeRunningImports(datasets);
        }
        return datasets;
      }
    }

    private void removeRunningImports(List<DatasetMapper.DatasetAttempt> datasets) {
      datasets.removeIf(d -> manager.isRunning(d.getKey()));
    }
  }


  @Override
  public boolean hasStarted() {
    return job != null && job.running;
  }
  
  @Override
  public void start() throws Exception {
    if (cfg.continuous.polling > 0) {
      LOG.info("Enable continuous importing");
      job = new ContinousImporterJob(cfg, manager, factory);
      thread = new Thread(job, THREAD_NAME);
      LOG.info("Start continuous importing with maxQueue={}, polling every {} minutes",
          job.cfg.threshold, job.cfg.polling
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

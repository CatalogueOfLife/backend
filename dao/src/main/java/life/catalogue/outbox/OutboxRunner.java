package life.catalogue.outbox;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.OutboxStatus;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.common.Managed;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.mapper.OutboxMapper;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Runs the durable commands of the outbox table, one {@link OutboxHandler} per {@link OutboxType}.
 *
 * Producers write a command in the transaction of the change that causes it, see {@link Outbox}, so it can neither
 * get lost when no app is listening, as an event of the broker can, nor exist without the change.
 * A single poll thread claims due commands with SKIP LOCKED and runs each on a virtual thread, at most
 * {@link OutboxHandler#concurrency()} per type. A successful command is deleted, a failing one retried with a backoff
 * and eventually marked failed, see {@link OutboxHandler} for the rules.
 *
 * This is the Outbox component and only ever started on the active app, so a command is run by one app only.
 * Stopping it hands every command still running back as pending, starting it takes over whatever a dead app left
 * running.
 */
public class OutboxRunner implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxRunner.class);
  private static final int MAX_ERROR_LENGTH = 2000;
  private static final long PURGE_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);

  private final OutboxConfig cfg;
  private final SqlSessionFactory factory;
  private final String instance;
  private final Map<OutboxType, OutboxHandler> handlers = new ConcurrentHashMap<>();
  private final Map<OutboxType, Semaphore> permits = new ConcurrentHashMap<>();
  private volatile ScheduledExecutorService poller;
  private volatile ExecutorService workers;
  private volatile boolean stopping;
  private long lastPurge;

  public OutboxRunner(OutboxConfig cfg, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
    // pid@host plus the start time, unique across the apps of a deploy and across restarts
    this.instance = ManagementFactory.getRuntimeMXBean().getName() + "/" + System.currentTimeMillis();
  }

  public OutboxRunner register(OutboxType type, OutboxHandler handler) {
    handlers.put(type, handler);
    permits.put(type, new Semaphore(Math.max(1, handler.concurrency())));
    return this;
  }

  public String getInstance() {
    return instance;
  }

  @Override
  public synchronized void start() throws Exception {
    if (hasStarted()) return;
    int reset;
    try (SqlSession session = factory.openSession(true)) {
      reset = session.getMapper(OutboxMapper.class).resetStale();
    }
    if (reset > 0) {
      LOG.warn("Took over {} outbox commands left running by a previous app", reset);
    }
    stopping = false;
    workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("outbox-", 0).factory());
    poller = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("outbox-poll", Thread.NORM_PRIORITY, true));
    poller.scheduleWithFixedDelay(this::poll, cfg.pollMillis, cfg.pollMillis, TimeUnit.MILLISECONDS);
    LOG.info("Started outbox runner {} for {}", instance, handlers.keySet());
  }

  @Override
  public synchronized void stop() throws Exception {
    if (!hasStarted()) return;
    stopping = true;
    poller.shutdownNow();
    poller.awaitTermination(5, TimeUnit.SECONDS);
    workers.shutdown();
    if (!workers.awaitTermination(cfg.stopGraceSeconds, TimeUnit.SECONDS)) {
      LOG.warn("Interrupt outbox commands still running after {}s", cfg.stopGraceSeconds);
      workers.shutdownNow();
      workers.awaitTermination(5, TimeUnit.SECONDS);
    }
    int released;
    try (SqlSession session = factory.openSession(true)) {
      released = session.getMapper(OutboxMapper.class).releaseMine(instance);
    }
    poller = null;
    workers = null;
    LOG.info("Stopped outbox runner {}, handed back {} running commands", instance, released);
  }

  @Override
  public boolean hasStarted() {
    return poller != null;
  }

  /**
   * One round: claims and starts every due command a handler is ready for.
   * Only the poll thread ever acquires permits, so claiming at most the free permits never blocks.
   */
  @VisibleForTesting
  void poll() {
    try {
      for (var e : handlers.entrySet()) {
        if (stopping) return;
        final OutboxType type = e.getKey();
        final OutboxHandler handler = e.getValue();
        if (!handler.isOnline()) continue;
        final Semaphore sem = permits.get(type);
        final int free = sem.availablePermits();
        if (free <= 0) continue;
        List<OutboxEntry> claimed;
        try (SqlSession session = factory.openSession(true)) {
          claimed = session.getMapper(OutboxMapper.class).claim(type, free, instance);
        }
        for (OutboxEntry entry : claimed) {
          sem.acquireUninterruptibly();
          try {
            workers.execute(() -> {
              try {
                run(handler, entry);
              } finally {
                sem.release();
              }
            });
          } catch (RejectedExecutionException ex) {
            // stopping - the command stays claimed by this instance and is handed back by stop()
            sem.release();
          }
        }
      }
      purgeDaily();
    } catch (Throwable t) {
      LOG.error("Outbox poll failed", t);
    }
  }

  @VisibleForTesting
  void run(OutboxHandler handler, OutboxEntry entry) {
    final int attempt = entry.getAttempts() + 1;
    try {
      LOG.debug("Run outbox command {}", entry);
      handler.handle(entry);
      complete(entry);

    } catch (RetryLaterException | UnavailableException | TooManyRequestsException e) {
      LOG.info("Outbox command {} cannot run now, retry in {}s: {}", entry, cfg.retryLaterSeconds, e.getMessage());
      retry(entry, 0, cfg.retryLaterSeconds, e);

    } catch (Exception e) {
      if (stopping && isInterruption(e)) {
        LOG.info("Outbox command {} interrupted by stop", entry);
        update(m -> m.requeue(entry.getId(), instance));
      } else {
        failOrRetry(handler, entry, attempt, e, handler.isPermanent(e));
      }

    } catch (Error e) {
      // recorded like any failure, else the command stays running and claimed by this app until it restarts.
      // Rethrown as BackgroundJob does: whether the JVM can carry on is not a single command's call
      failOrRetry(handler, entry, attempt, new ExecutionException(e), false);
      throw e;
    }
  }

  private void failOrRetry(OutboxHandler handler, OutboxEntry entry, int attempt, Exception e, boolean permanent) {
    if (permanent || attempt >= cfg.maxAttempts) {
      LOG.error("Outbox command {} failed for good after {} attempts", entry, attempt, e);
      update(m -> m.fail(entry.getId(), entry.getVersion(), instance, errorMessage(e)));
      try {
        handler.onFailed(entry, e);
      } catch (RuntimeException ex) {
        LOG.error("Failure notification for outbox command {} failed", entry, ex);
      }

    } else {
      long delay = handler.retryDelay(attempt).toSeconds();
      LOG.warn("Outbox command {} failed, attempt {} of {}, retry in {}s", entry, attempt, cfg.maxAttempts, delay, e);
      retry(entry, 1, (int) Math.min(delay, Integer.MAX_VALUE), e);
    }
  }

  private void complete(OutboxEntry entry) {
    update(m -> {
      if (m.complete(entry.getId(), entry.getVersion(), instance) == 0) {
        // a new request was merged in while it ran, run it again
        LOG.debug("Outbox command {} changed while running, requeue it", entry);
        return m.requeue(entry.getId(), instance);
      }
      return 1;
    });
  }

  private void retry(OutboxEntry entry, int inc, int delaySeconds, Exception e) {
    update(m -> m.retry(entry.getId(), instance, inc, delaySeconds, errorMessage(e)));
  }

  private int update(java.util.function.ToIntFunction<OutboxMapper> action) {
    try (SqlSession session = factory.openSession(true)) {
      return action.applyAsInt(session.getMapper(OutboxMapper.class));
    } catch (RuntimeException e) {
      // the command stays running and claimed by this instance, stop() or the next start() hands it back
      LOG.error("Failed to record the outcome of an outbox command", e);
      return 0;
    }
  }

  private void purgeDaily() {
    long now = System.currentTimeMillis();
    if (now - lastPurge > PURGE_INTERVAL_MS) {
      lastPurge = now;
      int purged = update(m -> m.purgeFailed(cfg.failedRetentionDays));
      if (purged > 0) {
        LOG.info("Purged {} failed outbox commands older than {} days", purged, cfg.failedRetentionDays);
      }
    }
  }

  private static String errorMessage(Exception e) {
    return StringUtils.abbreviate(e.toString(), MAX_ERROR_LENGTH);
  }

  private static boolean isInterruption(Throwable e) {
    Throwable t = e;
    while (t != null) {
      if (t instanceof InterruptedException || t instanceof InterruptedRuntimeException) {
        return true;
      }
      t = t.getCause();
    }
    return Thread.currentThread().isInterrupted();
  }

  public record Health(int pending, int overdue, int failed) {}

  public Health health() {
    try (SqlSession session = factory.openSession(true)) {
      var m = session.getMapper(OutboxMapper.class);
      return new Health(
        m.count(null, OutboxStatus.PENDING),
        m.countOverdue(cfg.overdueMinutes),
        m.count(null, OutboxStatus.FAILED)
      );
    }
  }

  public OutboxConfig getCfg() {
    return cfg;
  }
}

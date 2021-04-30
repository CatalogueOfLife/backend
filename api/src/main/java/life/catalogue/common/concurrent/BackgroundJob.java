package life.catalogue.common.concurrent;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.common.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BackgroundJob implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(BackgroundJob.class);

  private final UUID key = UUID.randomUUID();
  private final JobPriority priority;
  private final int userKey;
  private final LocalDateTime created;
  private JobStatus status;
  private LocalDateTime started;
  private LocalDateTime finished;
  private Exception error;
  private final List<Consumer<BackgroundJob>> finishedHandler = new ArrayList<>();

  public BackgroundJob(int userKey) {
    this(JobPriority.MEDIUM, userKey);
  }

  public BackgroundJob(@Nullable JobPriority priority, int userKey) {
    this.userKey = userKey;
    this.priority = priority == null ? JobPriority.MEDIUM : priority;
    this.status = JobStatus.WAITING;
    this.created = LocalDateTime.now();
  }

  public abstract void execute() throws Exception;

  public void addHandler(Consumer<BackgroundJob> finishedHandler) {
    this.finishedHandler.add(finishedHandler);
  }

  /**
   * Method to override if you want the job executor to reject duplicate jobs on submit that are already present in the queue
   * or are running already. By default jobs are compared by their key which is unique to every job instance.
   * @return true if its a duplicate that should be rejected
   */
  public boolean isDuplicate(BackgroundJob other) {
    return this.key.equals(other.key);
  }

  @Override
  public final void run() {
    try {
      LoggingUtils.setJobMDC(key, getClass());
      started = LocalDateTime.now();
      status = JobStatus.RUNNING;
      LOG.info("Started {} job {}", getClass().getSimpleName(), key);
      execute();
      status = JobStatus.FINISHED;
      LOG.info("Finished {} job {}", getClass().getSimpleName(), key);

    } catch (InterruptedException e) {
      status = JobStatus.CANCELED;
      LOG.warn("Interrupted {} job {}", getClass().getSimpleName(), key);

    } catch (Exception e) {
      status = JobStatus.FAILED;
      error = e;
      LOG.error("Error running {} job {}", getClass().getSimpleName(), key, e);

    } finally {
      finished = LocalDateTime.now();
      LoggingUtils.removeJobMDC();
      for (Consumer<BackgroundJob> h : finishedHandler) {
        h.accept(this);
      }
    }
  }

  @JsonProperty("job")
  public String getJobName() {
    return getClass().getSimpleName();
  }

  public UUID getKey() {
    return key;
  }

  public JobPriority getPriority() {
    return priority;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(JobStatus status) {
    this.status = status;
  }

  public Exception getError() {
    return error;
  }

  public LocalDateTime getCreated() {
    return created;
  }

  public LocalDateTime getStarted() {
    return started;
  }

  public LocalDateTime getFinished() {
    return finished;
  }

  public int getUserKey() {
    return userKey;
  }

  public boolean isQueued() {
    return status == JobStatus.WAITING;
  }

  public boolean isRunning() {
    return status == JobStatus.RUNNING;
  }

  /**
   * @return true if the job was done either by finishing successfully, failing or being canceled.
   */
  public boolean isStopped() {
    return !isRunning() && !isQueued();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BackgroundJob)) return false;
    BackgroundJob that = (BackgroundJob) o;
    return userKey == that.userKey &&
      Objects.equals(key, that.key) &&
      priority == that.priority &&
      Objects.equals(created, that.created) &&
      status == that.status &&
      Objects.equals(started, that.started) &&
      Objects.equals(finished, that.finished) &&
      Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, priority, userKey, created, status, started, finished, error);
  }
}

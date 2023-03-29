package life.catalogue.concurrent;

import com.codahale.metrics.Timer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.util.LoggingUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import life.catalogue.config.MailConfig;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

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
  // the following are added by the JobExecutor before a job is submitted
  private @Nullable EmailNotification emailer;
  // you can expect the following to exist and never be null!
  private JobConfig cfg;
  private User user;
  private Timer timer;

  protected BackgroundJob(int userKey) {
    this(JobPriority.MEDIUM, userKey);
  }

  protected BackgroundJob(@Nullable JobPriority priority, int userKey) {
    this.userKey = userKey;
    this.priority = priority == null ? JobPriority.MEDIUM : priority;
    this.status = JobStatus.WAITING;
    this.created = LocalDateTime.now();
  }

  public abstract void execute() throws Exception;

  void setEmailer(EmailNotification emailer) {
    this.emailer = emailer;
  }

  void setTimer(Timer timer) {
    this.timer = timer;
  }

  public void setCfg(JobConfig cfg) {
    this.cfg = cfg;
  }

  void setUser(User user) {
    this.user = user;
  }

  public User getUser() {
    return user;
  }

  /**
   * Final handler that can be implemented to e.g. persist jobs, or run notifications.
   * The final job status is set along with potential exceptions and timestamps.
   * The method should only be fired when the job is truely done, i.e. status.isDone().
   *
   * Blocked jobs will be resubmitted and only trigger the onFinish once when they finally run.
   */
  protected void onFinish() throws Exception {
    // dont persist by default
  }

  /**
   * Method to override if you want the job executor to reject duplicate jobs on submit that are already present in the queue
   * or are running already. By default jobs are compared by their key which is unique to every job instance.
   * @return true if its a duplicate that should be rejected
   */
  public boolean isDuplicate(BackgroundJob other) {
    return this.key.equals(other.key);
  }

  /**
   * Determine if all components needed to run this job are currently online.
   *
   * @throws UnavailableException if some component has not yet started
   **/
  public void assertComponentsOnline() throws UnavailableException {
  }

  @Override
  public final void run() {
    final Timer.Context ctxt = timer==null ? null : timer.time();
    try {
      LoggingUtils.setJobMDC(key, getClass());
      status = JobStatus.RUNNING;
      started = LocalDateTime.now();
      LOG.info("Started {} job {}", getClass().getSimpleName(), key);
      execute();
      status = JobStatus.FINISHED;
      LOG.info("Finished {} job {}", getClass().getSimpleName(), key);

    } catch (BlockedException e) {
      status = JobStatus.BLOCKED;
      LOG.info("Blocked {} job {}", getClass().getSimpleName(), key);
      // rethrow - we want this to surface to the JobExecutor which handles rescheduling
      throw e;

    } catch (InterruptedException e) {
      status = JobStatus.CANCELED;
      LOG.warn("Interrupted {} job {}", getClass().getSimpleName(), key);

    } catch (Exception e) {
      status = JobStatus.FAILED;
      error = e;
      LOG.error("Error running {} job {}", getClass().getSimpleName(), key, e);

    } finally {
      finished = LocalDateTime.now();
      if (status != JobStatus.BLOCKED) {
        try {
          onFinish();
        } catch (Exception e) {
          LOG.error("Failed to finish {} job {}", getClass().getSimpleName(), key, e);
        }

        // email notification
        if (emailer != null) {
          emailer.sendFinalEmail(this);
        } else {
          LOG.info("No emailer configured. Do not notify users about {} {} {}", getStatus(), getJobName(), getKey());
        }
        if (ctxt != null) {
          ctxt.stop(); // we dont want to measure blocked runs
        }
      }
      // will cause the dataset sifting appender reach end-of-life. It will linger for a few seconds.
      LOG.info(LoggingUtils.FINALIZE_SESSION_MARKER, "About to end {} {}", getJobName(), key);
      LoggingUtils.removeJobMDC();

      // copy job logs to download directory
      if (cfg != null) {
        File log = cfg.jobLog(key);
        if (log.exists()) {
          copyLogFile(log);
        }
      }
    }
  }

  /**
   * @param log Gzipped log file from appender log.gz
   */
  protected void copyLogFile(File log) {
    try {
      FileUtils.copyFile(log, cfg.downloadJobLog(key));
    } catch (IOException e) {
      LOG.error("Failed to copy job logs from {}", log, e);
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

  @JsonIgnore
  public boolean isQueued() {
    return status == JobStatus.WAITING;
  }

  @JsonIgnore
  public boolean isRunning() {
    return status == JobStatus.RUNNING;
  }

  /**
   * @return true if the job was done either by finishing successfully, failing or being canceled.
   */
  @JsonIgnore
  public boolean isStopped() {
    return !isRunning() && !isQueued();
  }

  protected void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException(getClass().getSimpleName() + " job " + key + " was cancelled while " + status);
    }
  }

  /**
   * Return the fixed prefix to be used for email notification freemarker templates.
   * If null is returned no notification will be done. The prefix will be appended with the final status of the job to find the appropriate template.
   *
   * See also getEmailData() whcih supplies the data model to render the freemarker template.
   */
  @JsonIgnore
  public String getEmailTemplatePrefix() {
    return null;
  }

  /**
   * Override this method to supply richer data if thats needed for the email templates
   * @param cfg
   * @return
   */
  public EmailNotification.EmailData getEmailData(MailConfig cfg) {
    return new EmailNotification.EmailData(this, user, cfg);
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

  @Override
  public String toString() {
    return getClass().getSimpleName() + " " + key + ": " + status;
  }
}

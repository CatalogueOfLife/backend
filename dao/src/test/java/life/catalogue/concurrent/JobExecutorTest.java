package life.catalogue.concurrent;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.dao.JobDao;
import life.catalogue.dao.UserCrudDao;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class JobExecutorTest {
  JobExecutor exec;
  ConcurrentLinkedQueue<UUID> finished;
  Map<UUID, JobStatus> status;
  final static User user = new User();

  static class Runme extends BackgroundJob {
    final int num;

    Runme(int num, JobPriority priority) {
      super(priority, JobExecutorTest.user.getKey());
      this.num = num;
    }

    @Override
    public void execute() throws Exception {
      System.out.println(num);
    }
  }

  @Before
  public void init() throws Exception {
    user.setKey(1);
    user.setUsername("foo");
    user.setLastname("Bar");
    UserCrudDao dao = mock(UserCrudDao.class);
    doReturn(user).when(dao).get(any());

    exec = new JobExecutor(JobConfig.withThreads(2), new MetricRegistry(), null, dao, null);
    exec.start();
    finished = new ConcurrentLinkedQueue<>();
    status = new ConcurrentHashMap<>();
  }

  @After
  public void down() throws Exception {
    exec.stop();
    System.out.println("\nJobs run:");
    finished.forEach(System.out::println);
    System.out.println("\n");
    status.forEach((k,v) -> {
      System.out.println(String.format("%s -> %s", k, v));
    });
  }

  @Test
  public void ordering() {
    List<JobExecutor.ComparableFutureTask> tasks = new ArrayList<>();
    tasks.add(new JobExecutor.ComparableFutureTask(new Runme(1, JobPriority.HIGH)));
    tasks.add(new JobExecutor.ComparableFutureTask(new Runme(2, JobPriority.LOW)));
    tasks.add(new JobExecutor.ComparableFutureTask(new Runme(3, JobPriority.HIGH)));
    tasks.add(new JobExecutor.ComparableFutureTask(new Runme(4, JobPriority.LOW)));
    tasks.add(new JobExecutor.ComparableFutureTask(new Runme(5, JobPriority.HIGH)));

    int h1 = tasks.get(0).hashCode();
    int h2 = tasks.get(1).hashCode();
    int h3 = tasks.get(2).hashCode();
    int h4 = tasks.get(3).hashCode();
    int h5 = tasks.get(4).hashCode();

    Collections.sort(tasks);

    assertEquals(h1, tasks.get(0).hashCode());
    assertEquals(h3, tasks.get(1).hashCode());
    assertEquals(h5, tasks.get(2).hashCode());
    assertEquals(h2, tasks.get(3).hashCode());
    assertEquals(h4, tasks.get(4).hashCode());
  }


  @Test
  public void exceptions() throws Exception {
    exec.submit(new FailJob());
    exec.submit(new FailJob());
  }

  /** Reports a stage like a real job does, so we can see what is left of it once it ends. */
  static class SteppingJob extends BackgroundJob {
    final boolean fail;
    String terminalStep;

    SteppingJob(boolean fail) {
      super(1);
      this.fail = fail;
    }

    @Override
    public void execute() throws Exception {
      setStep("downloading");
      if (fail) {
        throw new IllegalStateException("boom while downloading");
      }
    }

    @Override
    protected void onFinish() {
      if (terminalStep != null) {
        setStep(terminalStep);
      }
    }
  }

  /**
   * A step describes a running job, so a successful one must not keep the stage it happened to stop at -
   * that is how a finished import ended up reading "Finished - Downloading".
   */
  @Test
  public void stepClearedOnSuccess() throws Exception {
    var job = new SteppingJob(false);
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    assertNull(job.getStep());
  }

  /** onFinish runs after the clear, so a job can still report how it ended. */
  @Test
  public void terminalStepSurvives() throws Exception {
    var job = new SteppingJob(false);
    job.terminalStep = "unchanged";
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    assertEquals("unchanged", job.getStep());
  }

  /** Where a job stopped is the interesting part when it did not succeed, so that step is kept. */
  @Test
  public void stepKeptOnFailure() throws Exception {
    var job = new SteppingJob(true);
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals("downloading", job.getStep());
  }

  /** Dies from an Error rather than an Exception, like a job that exhausts the heap. */
  static class ErrorJob extends BackgroundJob {

    ErrorJob() {
      super(1);
    }

    @Override
    public void execute() throws Exception {
      setStep("inserting");
      throw new OutOfMemoryError("Java heap space");
    }
  }

  /**
   * An Error is no Exception, so a job killed by one used to leave its record as it was - still running,
   * no error, no finish time - which is how the ITIS import of 2026-08-31 lost its heap exhaustion.
   * Record it like any other failure, but let it keep flying: a broken JVM is not ours to swallow.
   */
  @Test
  public void errorRecordedAndRethrown() {
    var job = new ErrorJob();
    var err = assertThrows(OutOfMemoryError.class, job::run);
    assertEquals("Java heap space", err.getMessage());
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertSame(err, job.getError());
    assertEquals("inserting", job.getStep());
    assertNotNull(job.getFinished());
  }

  static class FailJob extends BackgroundJob {

    public FailJob() {
      super(1);
    }

    @Override
    public void execute() throws Exception {
      System.out.println("Run for fail " + getKey());
      throw new IllegalStateException(getClass().getSimpleName());
    }
  }

  class BlockJob extends DatasetBlockingJob {

    public BlockJob() {
      super(1, 1, JobPriority.MEDIUM);
      System.out.println(getClass().getSimpleName() + " " + getKey());
    }

    @Override
    protected void runWithLock() throws Exception {
      System.out.println("run " + getClass().getSimpleName() + " " + getKey());
    }

    @Override
    protected void onFinishLocked() throws Exception {
      if (getStatus().isDone()) {
        System.out.println("Done " + getClass().getSimpleName() + " " + getKey());
        finished.add(getKey());
      } else {
        System.out.println("Not done, "+getStatus()+ ": "+ getClass().getSimpleName() + " " + getKey());
      }
      status.put(getKey(), getStatus());
    }
  }

  class WaitJob extends BlockJob {
    final int ms;

    public WaitJob(int ms) {
      this.ms = ms;
    }

    @Override
    protected void runWithLock() throws Exception {
      TimeUnit.MILLISECONDS.sleep(ms);
    }
  }

  static class SleepJob extends BackgroundJob {
    final int ms;

    SleepJob(int ms) {
      super(1);
      this.ms = ms;
    }

    @Override
    public void execute() throws Exception {
      setStep("sleeping");
      TimeUnit.MILLISECONDS.sleep(ms);
    }
  }

  /**
   * Builds an executor with a mocked JobDao that records the job status at the time of every persistence call.
   */
  private JobExecutor persistingExecutor(int threads, Map<UUID, List<JobStatus>> persisted) throws Exception {
    UserCrudDao udao = mock(UserCrudDao.class);
    doReturn(user).when(udao).get(any());
    JobDao jdao = mock(JobDao.class);
    doAnswer(inv -> {
      BackgroundJob job = inv.getArgument(0);
      persisted.computeIfAbsent(job.getKey(), k -> new CopyOnWriteArrayList<>()).add(job.getStatus());
      return null;
    }).when(jdao).create(any(BackgroundJob.class));
    doAnswer(inv -> {
      BackgroundJob job = inv.getArgument(0);
      persisted.computeIfAbsent(job.getKey(), k -> new CopyOnWriteArrayList<>()).add(job.getStatus());
      return null;
    }).when(jdao).update(any(BackgroundJob.class));
    var ex = new JobExecutor(JobConfig.withThreads(threads), new MetricRegistry(), null, udao, jdao);
    ex.start();
    return ex;
  }

  private void awaitIdle(JobExecutor ex) throws InterruptedException {
    while (!ex.isIdle()) {
      TimeUnit.MILLISECONDS.sleep(5);
    }
  }

  @Test
  public void persistLifecycle() throws Exception {
    Map<UUID, List<JobStatus>> persisted = new ConcurrentHashMap<>();
    var ex = persistingExecutor(2, persisted);

    var good = new Runme(1, JobPriority.MEDIUM);
    var bad = new FailJob();
    ex.submit(good);
    ex.submit(bad);
    awaitIdle(ex);
    ex.stop();

    assertEquals(List.of(JobStatus.WAITING, JobStatus.RUNNING, JobStatus.FINISHED), persisted.get(good.getKey()));
    assertEquals(List.of(JobStatus.WAITING, JobStatus.RUNNING, JobStatus.FAILED), persisted.get(bad.getKey()));
  }

  @Test
  public void persistStep() throws Exception {
    Map<UUID, List<JobStatus>> persisted = new ConcurrentHashMap<>();
    var ex = persistingExecutor(2, persisted);

    var job = new SleepJob(5);
    ex.submit(job);
    awaitIdle(ex);
    ex.stop();

    // the setStep call adds one more RUNNING persistence in between
    assertEquals(List.of(JobStatus.WAITING, JobStatus.RUNNING, JobStatus.RUNNING, JobStatus.FINISHED), persisted.get(job.getKey()));
    // the step was persisted while running but cleared again on success, see stepClearedOnSuccess
    assertNull(job.getStep());
  }

  @Test
  public void persistCancelBeforeStart() throws Exception {
    Map<UUID, List<JobStatus>> persisted = new ConcurrentHashMap<>();
    var ex = persistingExecutor(1, persisted);

    // occupy the single thread, then queue another job and cancel it before it ever runs
    ex.submit(new SleepJob(200));
    var queued = new SleepJob(10);
    ex.submit(queued);
    var canceled = ex.cancel(queued.getKey(), user.getKey());

    assertEquals(JobStatus.CANCELED, canceled.getStatus());
    var statuses = persisted.get(queued.getKey());
    assertEquals(JobStatus.WAITING, statuses.get(0));
    assertEquals(JobStatus.CANCELED, statuses.get(statuses.size() - 1));

    awaitIdle(ex);
    ex.stop();
    assertTrue(persisted.get(queued.getKey()).stream().noneMatch(s -> s == JobStatus.RUNNING || s == JobStatus.FINISHED));
  }

  static class SerialJob extends BackgroundJob {
    static final Map<Object, AtomicInteger> RUNNING = new ConcurrentHashMap<>();
    static final AtomicBoolean VIOLATION = new AtomicBoolean();

    final Object serial;
    final int num;
    final int ms;
    final List<String> done;

    SerialJob(Object serial, int num, int ms, List<String> done) {
      super(1);
      this.serial = serial;
      this.num = num;
      this.ms = ms;
      this.done = done;
    }

    @Override
    public Object getSerialBy() {
      return serial;
    }

    @Override
    public void execute() throws Exception {
      var active = RUNNING.computeIfAbsent(serial, k -> new AtomicInteger());
      if (active.incrementAndGet() > 1) {
        VIOLATION.set(true);
      }
      try {
        TimeUnit.MILLISECONDS.sleep(ms);
        done.add(serial + "-" + num);
      } finally {
        active.decrementAndGet();
      }
    }
  }

  @Test
  public void serializedJobs() throws Exception {
    SerialJob.RUNNING.clear();
    SerialJob.VIOLATION.set(false);
    UserCrudDao dao = mock(UserCrudDao.class);
    doReturn(user).when(dao).get(any());
    var ex = new JobExecutor(JobConfig.withThreads(4), new MetricRegistry(), null, dao, null);
    ex.start();

    List<String> done = new CopyOnWriteArrayList<>();
    for (int num = 1; num <= 4; num++) {
      ex.submit(new SerialJob("a", num, 20, done));
      ex.submit(new SerialJob("b", num, 10, done));
    }
    while (!ex.isIdle()) {
      TimeUnit.MILLISECONDS.sleep(5);
    }
    ex.stop();

    assertFalse("two jobs with the same serial key ran concurrently", SerialJob.VIOLATION.get());
    assertEquals(8, done.size());
    // per serial key the completion order must follow the submission order
    for (String key : List.of("a", "b")) {
      var perKey = done.stream().filter(d -> d.startsWith(key)).collect(Collectors.toList());
      assertEquals(List.of(key + "-1", key + "-2", key + "-3", key + "-4"), perKey);
    }
  }

  @Test
  public void cancelSerializedJobs() throws Exception {
    SerialJob.RUNNING.clear();
    SerialJob.VIOLATION.set(false);
    UserCrudDao dao = mock(UserCrudDao.class);
    doReturn(user).when(dao).get(any());
    var ex = new JobExecutor(JobConfig.withThreads(2), new MetricRegistry(), null, dao, null);
    ex.start();

    List<String> done = new CopyOnWriteArrayList<>();
    var j1 = new SerialJob("a", 1, 200, done);
    var j2 = new SerialJob("a", 2, 10, done);
    var j3 = new SerialJob("a", 3, 10, done);
    ex.submit(j1);
    ex.submit(j2);
    ex.submit(j3);

    // j2 is parked behind the running j1 - cancelling it must not block j3
    var canceled = ex.cancel(j2.getKey(), user.getKey());
    assertEquals(JobStatus.CANCELED, canceled.getStatus());

    while (!ex.isIdle()) {
      TimeUnit.MILLISECONDS.sleep(5);
    }
    ex.stop();

    assertFalse(SerialJob.VIOLATION.get());
    assertEquals(List.of("a-1", "a-3"), done);
  }

  @Test
  public void resubmitBlocked() throws Exception {
    exec.submit(new BlockJob());
    exec.submit(new WaitJob(25));
    exec.submit(new WaitJob(10));
    exec.submit(new BlockJob());
    exec.submit(new BlockJob());
    exec.submit(new BlockJob());

    // we need to wait before we can shutdown the executor - otherwise job resubmissions will not work
    while (!exec.isIdle()) {
      TimeUnit.MILLISECONDS.sleep(5);
    }
    exec.stop();
    assertEquals(6, finished.size());
  }

  /**
   * Records that it ran, so a test can tell a discarded job from an executed one.
   * Duplicates are keyed by num rather than the job key, so a resubmit of "the same" job is detectable.
   */
  static class MarkJob extends BackgroundJob {
    static final List<Integer> RAN = new CopyOnWriteArrayList<>();
    final int num;
    final int ms;

    MarkJob(int num, int ms) {
      super(1);
      this.num = num;
      this.ms = ms;
    }

    @Override
    public void execute() throws Exception {
      RAN.add(num);
      if (ms > 0) {
        TimeUnit.MILLISECONDS.sleep(ms);
      }
    }

    @Override
    public boolean isDuplicate(BackgroundJob other) {
      return other instanceof MarkJob && ((MarkJob) other).num == num;
    }
  }

  private JobExecutor singleThreaded() throws Exception {
    UserCrudDao dao = mock(UserCrudDao.class);
    doReturn(user).when(dao).get(any());
    return new JobExecutor(JobConfig.withThreads(1), new MetricRegistry(), null, dao, null);
  }

  /** Submits a long job and waits until the single worker really picked it up, so the next submit queues. */
  private MarkJob occupyTheWorker(JobExecutor ex, int ms) throws Exception {
    var running = new MarkJob(1, ms);
    ex.submit(running);
    while (!MarkJob.RAN.contains(1)) {
      TimeUnit.MILLISECONDS.sleep(5);
    }
    return running;
  }

  @Test
  public void submitBeforeStartIs503() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    // the constructor no longer starts the pools: cancelStale() must not fire while another app still runs jobs
    assertFalse(ex.hasStarted());
    assertThrows(UnavailableException.class, () -> ex.submit(new MarkJob(1, 0)));

    ex.start();
    assertTrue(ex.hasStarted());
    ex.submit(new MarkJob(1, 0));
    awaitIdle(ex);
    ex.stop();
  }

  @Test
  public void stopDoesNotStartQueuedJobs() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    occupyTheWorker(ex, 300);

    var queued = new MarkJob(2, 0);
    ex.submit(queued);
    assertEquals(1, ex.queueSize());

    ex.stop();

    // the orderly ThreadPoolExecutor.shutdown() keeps draining its work queue, so it would have run job 2 too
    assertEquals(List.of(1), MarkJob.RAN);
    // and a job that never reached run() still gets a final state, or its row would stay waiting forever
    assertEquals(JobStatus.CANCELED, queued.getStatus());
    assertEquals(0, ex.queueSize());
  }

  @Test
  public void stopInterruptsRunningJobs() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    occupyTheWorker(ex, 30000);

    long start = System.currentTimeMillis();
    ex.stop();
    long took = System.currentTimeMillis() - start;

    // the orderly shutdown() waits MILLIS_TO_DIE for the sleeping job before it forces anything,
    // so stopping an app with a long job used to take 12s per lane instead of interrupting it
    assertTrue("stop took " + took + "ms, it should have interrupted the running job", took < 5000);
  }

  @Test
  public void restartClearsQueueAndFutures() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    occupyTheWorker(ex, 200);

    var queued = new MarkJob(2, 0);
    ex.submit(queued);

    ex.stop();
    ex.start();

    // the queued job was discarded, not drained onto a worker on the way out
    assertEquals(List.of(1), MarkJob.RAN);
    // no phantoms of the discarded job survive the restart
    assertTrue(ex.getQueue().isEmpty());
    assertEquals(0, ex.queueSize());
    assertFalse(ex.exists(queued.getKey()));
    assertNull(ex.getJob(queued.getKey()));
    // so an identical job is not rejected as a duplicate of one that will never run
    ex.submit(new MarkJob(2, 0));
    awaitIdle(ex);
    ex.stop();
  }

  @Test
  public void pausedJobsDoNotRunAndResumeLater() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    var running = occupyTheWorker(ex, 100);

    ex.pause();
    assertTrue(ex.isPaused());
    ex.submit(new MarkJob(2, 0));
    ex.submit(new MarkJob(3, 0));

    // the job that was already running is left to finish, so the executor quiesces
    assertTrue(ex.awaitQuiesced(10, TimeUnit.SECONDS));
    assertEquals(JobStatus.FINISHED, running.getStatus());
    // but nothing new starts, however long we wait
    TimeUnit.MILLISECONDS.sleep(200);
    assertEquals(List.of(1), MarkJob.RAN);
    // and the queue is kept rather than discarded - this is the difference to stop()
    assertEquals(2, ex.getQueue().size());

    ex.resume();
    assertFalse(ex.isPaused());
    awaitIdle(ex);
    assertEquals(Set.of(1, 2, 3), new HashSet<>(MarkJob.RAN));
    ex.stop();
  }

  @Test
  public void pausedExecutorStillAcceptsSubmissions() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    ex.pause();

    // a maintenance window must not cost users their requests, so submits are queued rather than rejected
    ex.submit(new MarkJob(7, 0));
    assertEquals(1, ex.getQueue().size());
    assertTrue(ex.awaitQuiesced(10, TimeUnit.SECONDS));
    assertTrue(MarkJob.RAN.isEmpty());

    ex.resume();
    awaitIdle(ex);
    assertEquals(List.of(7), MarkJob.RAN);
    ex.stop();
  }

  @Test
  public void quiescedWaitsForTheRunningJobOnly() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    occupyTheWorker(ex, 400);
    ex.submit(new MarkJob(2, 0));
    ex.pause();

    // still running, so not quiesced yet
    assertFalse(ex.isQuiesced());
    assertTrue(ex.awaitQuiesced(10, TimeUnit.SECONDS));
    // queued work is deliberately not part of the question - it is held back, not lost
    assertFalse(ex.getQueue().isEmpty());
    ex.stop();
  }

  @Test
  public void stopReleasesWorkersParkedOnThePauseGate() throws Exception {
    MarkJob.RAN.clear();
    var ex = singleThreaded();
    ex.start();
    ex.pause();
    ex.submit(new MarkJob(1, 0));

    // a worker takes the job and parks on the gate; stop() has to let it go or it would wait MILLIS_TO_DIE
    long start = System.currentTimeMillis();
    ex.stop();
    long took = System.currentTimeMillis() - start;
    assertTrue("stop took " + took + "ms with a worker parked on the pause gate", took < 5000);
  }

  @Test
  public void pauseIsIdempotent() throws Exception {
    var ex = singleThreaded();
    ex.start();
    ex.pause();
    ex.pause();
    assertTrue(ex.isPaused());
    ex.resume();
    ex.resume();
    assertFalse(ex.isPaused());
    ex.stop();
  }
}

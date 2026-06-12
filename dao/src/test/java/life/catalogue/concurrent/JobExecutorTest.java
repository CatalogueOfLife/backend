package life.catalogue.concurrent;

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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertEquals;
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
    return new JobExecutor(JobConfig.withThreads(threads), new MetricRegistry(), null, udao, jdao);
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
    assertEquals("sleeping", job.getStep());
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
}
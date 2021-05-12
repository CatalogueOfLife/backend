package life.catalogue.concurrent;

import life.catalogue.api.vocab.JobStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class JobExecutorTest {
  JobExecutor exec;
  ConcurrentLinkedQueue<UUID> finished;
  Map<UUID, JobStatus> status;

  static class Runme extends BackgroundJob {
    final int num;

    Runme(int num, JobPriority priority) {
      super(priority, 1);
      this.num = num;
    }

    @Override
    public void execute() throws Exception {
      System.out.println(num);
    }
  }

  @Before
  public void init() {
    exec = new JobExecutor(JobConfig.withThreads(2));
    finished = new ConcurrentLinkedQueue<>();
    status = new ConcurrentHashMap<>();
  }

  @After
  public void down() {
    exec.close();
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
    protected void onFinish() throws Exception {
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
    exec.close();
    assertEquals(6, finished.size());
  }
}
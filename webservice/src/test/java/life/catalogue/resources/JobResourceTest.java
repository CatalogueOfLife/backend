package life.catalogue.resources;

import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class JobResourceTest {

  static class TestJob extends BackgroundJob {
    private final JobLane lane;

    TestJob(JobLane lane, JobStatus status) {
      super(1);
      this.lane = lane;
      setStatus(status);
    }

    @Override
    public JobLane getLane() {
      return lane;
    }

    @Override
    public void execute() throws Exception {
      // never actually run
    }
  }

  /**
   * The job types served by /job/types come from a classpath scan, so they silently go stale
   * if the scan stops finding the job packages. Assert on a few known jobs from different modules.
   */
  @Test
  public void scanJobTypes() {
    List<String> types = JobResource.scanJobTypes();
    assertTrue("expected a good number of job types, but got " + types, types.size() > 20);
    // jobs from the dao, core and importer modules
    assertTrue(types.contains("ImportJob"));
    assertTrue(types.contains("SectorSync"));
    assertTrue(types.contains("SectorDelete"));
    assertTrue(types.contains("DeleteDatasetJob"));
    assertTrue(types.contains("GbifSyncJob"));
    // nested job classes count too - job_class holds the simple name either way,
    // so a job declared inside a factory must be as filterable as a top level one
    assertTrue(types.contains("MatcherBuildJob"));
    assertTrue(types.contains("ReconcileJob"));
    // anonymous and local classes have no usable simple name
    assertFalse(types.contains(""));
    // abstract base classes are not job types - they never appear in job_class
    assertFalse(types.contains("BackgroundJob"));
    assertFalse(types.contains("DatasetJob"));
    assertFalse(types.contains("DatasetBlockingJob"));
    assertFalse(types.contains("GlobalBlockingJob"));
    assertFalse(types.contains("SectorRunnable"));
    assertFalse(types.contains("AbstractProjectCopy"));
    // sorted and free of duplicates
    assertEquals(types.stream().sorted().toList(), types);
    assertEquals(types.stream().distinct().count(), types.size());
  }

  /**
   * The counts must describe the jobs actually handed to the state, not the whole executor,
   * so that GET /job?datasetKey=x cannot report a filtered array next to a global total.
   */
  @Test
  public void queueCountsMatchTheJobsGiven() {
    var all = List.<BackgroundJob>of(
      new TestJob(JobLane.IMPORT, JobStatus.RUNNING),
      new TestJob(JobLane.IMPORT, JobStatus.WAITING),
      new TestJob(JobLane.IMPORT, JobStatus.WAITING),
      new TestJob(JobLane.SYNC, JobStatus.WAITING),
      new TestJob(JobLane.DEFAULT, JobStatus.RUNNING)
    );
    var state = new JobResource.JobQueueState(all);
    assertEquals(2, state.running.size());
    assertEquals(3, state.queued.size());
    assertEquals(3, state.queuedTotal);
    assertEquals(state.queued.size(), state.queuedTotal);
    assertEquals(Integer.valueOf(2), state.queuedCounts.get(JobLane.IMPORT));
    assertEquals(Integer.valueOf(1), state.queuedCounts.get(JobLane.SYNC));
    // every lane stays in the map, so the payload shape does not change with the load
    assertEquals(Integer.valueOf(0), state.queuedCounts.get(JobLane.DEFAULT));
    assertEquals(JobLane.values().length, state.queuedCounts.size());

    // the same jobs narrowed down, e.g. by a datasetKey, must narrow the counts with them
    var narrowed = new JobResource.JobQueueState(all.subList(0, 2));
    assertEquals(1, narrowed.running.size());
    assertEquals(1, narrowed.queuedTotal);
    assertEquals(Integer.valueOf(1), narrowed.queuedCounts.get(JobLane.IMPORT));
    assertEquals(Integer.valueOf(0), narrowed.queuedCounts.get(JobLane.SYNC));
  }

  /**
   * A job blocked on a dataset lock is still waiting in its lane queue. It used to fall out of both
   * lists, since only WAITING counted as queued, leaving it invisible in the live queue.
   */
  @Test
  public void blockedJobsAreQueued() {
    var state = new JobResource.JobQueueState(List.<BackgroundJob>of(
      new TestJob(JobLane.DEFAULT, JobStatus.BLOCKED),
      new TestJob(JobLane.DEFAULT, JobStatus.WAITING)
    ));
    assertTrue(state.running.isEmpty());
    assertEquals(2, state.queued.size());
    assertEquals(2, state.queuedTotal);
    assertEquals(Integer.valueOf(2), state.queuedCounts.get(JobLane.DEFAULT));
  }

  /**
   * Jobs that just ended can linger in a queue snapshot - they belong in neither list.
   */
  @Test
  public void finishedJobsAreDropped() {
    var state = new JobResource.JobQueueState(List.<BackgroundJob>of(
      new TestJob(JobLane.DEFAULT, JobStatus.FINISHED),
      new TestJob(JobLane.DEFAULT, JobStatus.CANCELED),
      new TestJob(JobLane.DEFAULT, JobStatus.FAILED)
    ));
    assertTrue(state.running.isEmpty());
    assertTrue(state.queued.isEmpty());
    assertEquals(0, state.queuedTotal);
  }
}

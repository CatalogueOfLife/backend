package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class PersonHarvestCronTest {

  /** a cron job that throws is unscheduled, so a refused submission must not end the harvests */
  @Test
  public void submitsAndSurvivesARefusal() {
    var exec = mock(JobExecutor.class);
    var job = mock(BackgroundJob.class);
    var cron = new PersonHarvestCron(exec, () -> job, 7);
    cron.run();
    verify(exec).submit(job);
    doThrow(new IllegalArgumentException("An identical job is queued already")).when(exec).submit(job);
    cron.run();
    verify(exec, times(2)).submit(job);
  }
}

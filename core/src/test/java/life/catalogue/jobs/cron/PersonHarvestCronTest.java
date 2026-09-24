package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import java.time.LocalDateTime;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class PersonHarvestCronTest {

  /** never harvested, or the last harvest older than the interval: a harvest is due */
  @Test
  public void submitsWhenDue() {
    var exec = mock(JobExecutor.class);
    var job = mock(BackgroundJob.class);
    new PersonHarvestCron(exec, () -> null, () -> job, 7).run();
    new PersonHarvestCron(exec, () -> LocalDateTime.now().minusDays(8), () -> job, 7).run();
    verify(exec, times(2)).submit(job);
  }

  /** a deploy restarts every schedule, so the last harvest decides, not the time the server started */
  @Test
  public void notBeforeTheInterval() {
    var exec = mock(JobExecutor.class);
    new PersonHarvestCron(exec, () -> LocalDateTime.now().minusDays(3), () -> mock(BackgroundJob.class), 7).run();
    verifyNoInteractions(exec);
  }

  /** a cron job that throws is unscheduled, so a refused submission must not end the harvests */
  @Test
  public void survivesARefusal() {
    var exec = mock(JobExecutor.class);
    var job = mock(BackgroundJob.class);
    doThrow(new IllegalArgumentException("An identical job is queued already")).when(exec).submit(job);
    var cron = new PersonHarvestCron(exec, () -> null, () -> job, 7);
    cron.run();
    cron.run();
    verify(exec, times(2)).submit(job);
  }
}

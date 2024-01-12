package life.catalogue.admin.jobs.cron;

import java.util.concurrent.TimeUnit;

public abstract class CronJob implements Runnable {
  private final int delay;
  private final int frequency;
  private final TimeUnit frequencyUnit;

  public CronJob(int frequency, TimeUnit frequencyUnit) {
    this(0, frequency, frequencyUnit);
  }
  public CronJob(int delay, int frequency, TimeUnit frequencyUnit) {
    this.delay = delay;
    this.frequency = frequency;
    this.frequencyUnit = frequencyUnit;
  }

  public int getDelay() {
    return delay;
  }

  public int getFrequency() {
    return frequency;
  }

  public TimeUnit getFrequencyUnit() {
    return frequencyUnit;
  }
}

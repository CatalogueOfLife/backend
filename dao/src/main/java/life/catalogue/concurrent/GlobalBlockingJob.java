package life.catalogue.concurrent;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks Runnable classes that can only run one at a time.
 */
public abstract class GlobalBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalBlockingJob.class);

  public GlobalBlockingJob(int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
  }

  public boolean isDuplicate(BackgroundJob other) {
    return getClass().equals(other.getClass());
  }

}

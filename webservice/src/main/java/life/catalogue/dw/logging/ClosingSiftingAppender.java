package life.catalogue.dw.logging;

import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * A SiftingAppender that closes nested appenders immediately when the EOL marker is seen.
 * The original SiftingAppender places these into a lingering map for 10s before they get removed and closed.
 */
public class ClosingSiftingAppender extends SiftingAppender {

  @Override
  protected void append(ILoggingEvent event) {
    super.append(event);
    if (eventMarksEndOfLife(event)) {
      getAppenderTracker().removeStaleComponents(Long.MAX_VALUE);
    }
  }

}

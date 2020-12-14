package life.catalogue.config;

import java.time.LocalDateTime;

/**
 *
 */
public class ReleaseIdConfig {
  public boolean restart = false;
  // the date we first deployed stable ids in releases - we ignore older ids than this date
  public LocalDateTime since;
  // id start
  public int start = 0;
}

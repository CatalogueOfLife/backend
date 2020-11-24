package life.catalogue.config;

import java.time.LocalDateTime;
import java.util.Map;

/**
 *
 */
public class ReleaseIdConfig {
  public boolean restart = false;
  // the date we first deployed stable ids in releases - we ignore older ids than this date
  public LocalDateTime since;
  // id start
  public int start = 0;
  // map of names index ids to preferred usage ids
  public Map<Integer, String> map;
}

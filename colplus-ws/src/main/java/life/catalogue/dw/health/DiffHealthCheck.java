package life.catalogue.dw.health;


import com.codahale.metrics.health.HealthCheck;
import life.catalogue.db.tree.DiffService;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class DiffHealthCheck extends HealthCheck {
  
  private final DiffService diff;
  
  public DiffHealthCheck(DiffService diff) {
    this.diff = diff;
  }
  
  @Override
  protected Result check() {
    try {
      String version = diff.diffBinaryVersion();
      if (version != null && version.startsWith("diff")) {
        return Result.healthy();
      }
      return Result.unhealthy("Bad version:\n" + version);
      
    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}
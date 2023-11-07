package life.catalogue.dw.health;


import life.catalogue.printer.BaseDiffService;

import com.codahale.metrics.health.HealthCheck;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class DiffHealthCheck extends HealthCheck {
  
  private final BaseDiffService diff;
  
  public DiffHealthCheck(BaseDiffService diff) {
    this.diff = diff;
  }
  
  @Override
  protected Result check() throws Exception {
    String version = diff.diffBinaryVersion();
    if (version != null && version.startsWith("diff")) {
      return Result.healthy();
    }
    return Result.unhealthy("Bad version:\n" + version);
  }
}
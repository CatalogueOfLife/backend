package org.col.csl;


import java.util.Optional;

import com.codahale.metrics.health.HealthCheck;
import org.col.api.model.CslData;
import org.col.parser.Parser;

/**
 * Calls the anystyle service with a known citation to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class AnystyleHealthCheck extends HealthCheck {
  private final Parser<CslData> anystyle;

  public AnystyleHealthCheck(Parser<CslData> anystyle) {
    this.anystyle = anystyle;
  }

  @Override
  protected Result check() throws Exception {
    try {
      Optional<CslData> result = anystyle.parse("Turing, Alan, Computing Machinery and Intelligence, Mind 59, pp 433-460 (1950)");
      if (result.isPresent()) {
        return Result.healthy();
      }
      return Result.unhealthy("Missing result");

    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}
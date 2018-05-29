package org.col.csl;


import java.util.Optional;

import com.codahale.metrics.health.HealthCheck;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.CslData;
import org.col.db.mapper.Ping;

/**
 * Calls the anystyle service with a known citation to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class AnystyleHealthCheck extends HealthCheck {
  private final AnystyleParserWrapper anystyle;

  public AnystyleHealthCheck(AnystyleParserWrapper anystyle) {
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
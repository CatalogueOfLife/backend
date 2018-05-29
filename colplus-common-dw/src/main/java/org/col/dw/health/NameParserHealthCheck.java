package org.col.dw.health;


import java.util.Optional;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Lists;
import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.col.parser.NameParser;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class NameParserHealthCheck extends HealthCheck {

  private final NameParser parser = NameParser.PARSER;

  @Override
  protected Result check() throws Exception {
    try {
      Optional<NameAccordingTo> result = parser.parse("Abies alba (L.) Mill.  sec Döring 1999");
      if (result.isPresent()) {
        Name name = result.get().getName();
        if (name.isBinomial() &&
            name.getGenus().equals("Abies") &&
            name.getSpecificEpithet().equals("alba") &&
            name.getBasionymAuthorship().getAuthors().equals(Lists.newArrayList("L.")) &&
            name.getCombinationAuthorship().getAuthors().equals(Lists.newArrayList("Mill.")) &&
            result.get().getAccordingTo().equals("sec Döring 1999")
        ) {
          return Result.healthy();
        }
        return Result.unhealthy("Wrong result: " + name);

      }
      return Result.unhealthy("Missing result");

    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}
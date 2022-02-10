package life.catalogue.dw.health;


import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Lists;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.coldp.NameParser;

import org.gbif.nameparser.api.Rank;

import java.util.Optional;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class NameParserHealthCheck extends HealthCheck {
  
  private final NameParser parser = NameParser.PARSER;
  
  @Override
  protected Result check() throws Exception {
    Optional<ParsedNameUsage> result = parser.parse("Abies alba (L.) Mill. sec Döring 1999", Rank.SPECIES, null, IssueContainer.VOID);
    if (result.isPresent()) {
      Name name = result.get().getName();
      if (name.isBinomial() &&
          name.getGenus().equals("Abies") &&
          name.getSpecificEpithet().equals("alba") &&
          name.getBasionymAuthorship().getAuthors().equals(Lists.newArrayList("L.")) &&
          name.getCombinationAuthorship().getAuthors().equals(Lists.newArrayList("Mill.")) &&
          result.get().getTaxonomicNote().equals("sec Döring 1999")
          ) {
        return Result.healthy();
      }
      return Result.unhealthy("Wrong result: " + name.toStringComplete());

    }
    return Result.unhealthy("Missing result");
  }
}
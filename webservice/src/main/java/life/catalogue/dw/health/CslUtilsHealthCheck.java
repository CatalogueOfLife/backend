package life.catalogue.dw.health;


import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.common.csl.CslUtil;

import com.codahale.metrics.health.HealthCheck;

import de.undercouch.citeproc.csl.CSLType;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class CslUtilsHealthCheck extends HealthCheck {
  private final CslData csl = new CslData();
  
  public CslUtilsHealthCheck() {
    csl.setTitle("Test the real thing");
    csl.setType(CSLType.ARTICLE);
    csl.setContainerTitle("Proceedings of Nature in Space");
    csl.setVolume("42");
    csl.setIssued(new CslDate());
    csl.getIssued().setDateParts(new int[1][3]);
    csl.getIssued().getDateParts()[0] = new int[]{1999, 5, 12};
  }
  
  @Override
  protected Result check() throws Exception {
    String cite = CslUtil.buildCitation(csl);
    if (cite != null) {
        return Result.healthy();
    }
    return Result.unhealthy("Missing citation result");
  }
}
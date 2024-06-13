package life.catalogue.dw.health;


import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import com.codahale.metrics.health.HealthCheck;

/**
 * Checks that the names index is online and working fine.
 * If the names index is offline or has never been started the result will be healthy!
 * Otherwise a single unhealthy result will cause the healthchecks to return a 500 error and our deploy scripts will fail
 * as we do not startup the names index automatically.
 */
public class NamesIndexHealthCheck extends HealthCheck {
  
  private final NameIndex nidx;
  private final Name name = new Name();
  
  public NamesIndexHealthCheck(NameIndex nidx) {
    this.nidx = nidx;
    name.setUninomial("Animalia");
    name.setRank(Rank.KINGDOM);
    name.setType(NameType.SCIENTIFIC);
    name.rebuildScientificName();
  }
  
  @Override
  protected Result check() throws Exception {
    if (nidx.hasStarted()) {
      NameMatch res = nidx.match(name, false, false);
      if (res.hasMatch()) {
        return Result.healthy("%s names, created %s", nidx.size(), nidx.created());
      }
      return Result.unhealthy("Cannot match %s", name.getScientificName());
    }
    return Result.healthy("Names Index is offline");
  }
}
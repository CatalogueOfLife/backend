package life.catalogue.dw.health;


import com.codahale.metrics.health.HealthCheck;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.matching.NameIndexImpl;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

/**
 * Checks that the names index is online and working fine.
 * If the names index is offline or has never been started the result will be healthy!
 * Otherwise a single unhealthy result will cause the healthchecks to return a 500 error and our deploy scripts will fail
 * as we do not startup the names index automatically.
 */
public class NamesIndexHealthCheck extends HealthCheck {
  
  private final NameIndexImpl nidx;
  private final Name name = new Name();
  
  public NamesIndexHealthCheck(NameIndexImpl nidx) {
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
        return Result.healthy("%s names", nidx.size());
      }
      return Result.unhealthy("Cannot match %s", name.getScientificName());
    }
    return Result.healthy("Names Index is offline");
  }
}
package life.catalogue.dw.health;


import com.codahale.metrics.health.HealthCheck;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.matching.NameIndex;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

/**
 * Calls the name parser with a known binomial to check its health.
 * Any non-exceptional and non-empty response, in any amount of time, is treated as a healthy response.
 * Any exceptional or empty response is treated as an unhealthy response.
 */
public class NamesIndexHealthCheck extends HealthCheck {
  
  private final NameIndex nidx;
  private final Name name = new Name();
  
  public NamesIndexHealthCheck(NameIndex nidx) {
    this.nidx = nidx;
    name.setUninomial("Animalia");
    name.setRank(Rank.KINGDOM);
    name.setType(NameType.SCIENTIFIC);
    name.updateNameCache();
  }
  
  @Override
  protected Result check() {
    try {
      NameMatch res = nidx.match(name, false, false);
      if (res.hasMatch()) {
        return Result.healthy("%s names", nidx.size());
      }
      return Result.unhealthy("Cannot match %s", name.getScientificName());
      
    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}
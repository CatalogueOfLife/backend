package life.catalogue.assembly;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NomCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoLUsageRules {
  private static final Logger LOG = LoggerFactory.getLogger(CoLUsageRules.class);

  public static void apply(NameUsageBase u, TreeHandler.Usage parent) {
    Name n = u.getName();
    // change tax status of manuscript names
    if (NomStatus.MANUSCRIPT == n.getNomStatus()) {
      if (!u.getStatus().isSynonym()) {
        //u.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        LOG.info("accepted manuscript name found: {}", u.getLabel());
      }
    }
    // botanical rules only
    if (NomCode.BOTANICAL == n.getCode()) {
      // remove authorship for a botanical autonym
      if (n.isAutonym() && n.getAuthorship() != null) {
        n.setAuthorship(null);
        n.setCombinationAuthorship(null);
        n.setBasionymAuthorship(null);
        n.setSanctioningAuthor(null);
        LOG.info("remove authorship from botanical autonym {}", u.getLabel());
      }
    }
  }
}

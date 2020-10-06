package life.catalogue.assembly;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.NomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoLUsageRules {
  private static final Logger LOG = LoggerFactory.getLogger(CoLUsageRules.class);

  public static void apply(NameUsageBase u, TreeCopyHandler.Usage parent) {
    Name n = u.getName();
    // change tax status of manuscript names
    if (NomStatus.MANUSCRIPT == n.getNomStatus()) {
      if (!u.getStatus().isSynonym()) {
        //u.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        LOG.info("accepted manuscript name found: {}", u.getLabel());
      }
    }
  }
}

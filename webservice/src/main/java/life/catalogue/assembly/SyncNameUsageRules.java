package life.catalogue.assembly;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncNameUsageRules {
  private static final Logger LOG = LoggerFactory.getLogger(SyncNameUsageRules.class);

  public static void applyAlways(NameUsageBase u) {
    Name n = u.getName();
    // botanical rules only
    if (NomCode.BOTANICAL == n.getCode()) {
      // remove authorship for a botanical autonym
      if (n.isAutonym() && n.getAuthorship() != null) {
        n.setAuthorship(null);
        n.setCombinationAuthorship(null);
        n.setBasionymAuthorship(null);
        n.setSanctioningAuthor(null);
        LOG.debug("remove authorship from botanical autonym {}", u.getLabel());
      }
    }
    // change tax status of manuscript names
    if (NomStatus.MANUSCRIPT == n.getNomStatus()) {
      if (!u.getStatus().isSynonym()) {
        u.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        LOG.debug("accepted manuscript name found: {}", u.getLabel());
      }
    }
    // change tax status of original spelling variations
    if (Boolean.TRUE.equals(u.getName().isOriginalSpelling())) {
      if (u.getStatus().isTaxon()) {
        u.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        LOG.debug("accepted original spelling found: {}", u.getLabel());
      } else {
        // TODO: create orth var name relation
      }
    }
    // fix all caps uninomials
    if (n.getType().isParsable() && n.isParsed() && n.getUninomial() != null && StringUtils.isAllUpperCase(n.getUninomial())) {
      n.setUninomial(StringUtils.capitalize(n.getUninomial().trim().toLowerCase()));
      LOG.debug("All capital {} {} converted to {}", n.getRank(), n.getScientificName(), n.getUninomial());
      n.rebuildScientificName();
    }
  }

}

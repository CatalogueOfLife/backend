package life.catalogue.doi;

import de.undercouch.citeproc.csl.CSLItemData;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;

/**
 * A CrossRef DOI resolver that can return citation metadata for most (all?) DOIs.
 */
public class Resolver {

  public Citation resolve(DOI doi) {
    return new Citation();
  }
}

package life.catalogue.metadata;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;

/**
 * A CrossRef DOI resolver that can return citation metadata for most (all?) DOIs.
 */
public class DoiResolver {

  public Citation resolve(DOI doi) {
    var c = new Citation();
    c.setId(doi.getDoiName());
    c.setDoi(doi);
    return c;
  }
}

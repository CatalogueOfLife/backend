package life.catalogue.metadata;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;

import javax.ws.rs.client.Client;

/**
 * A CrossRef DOI resolver that can return citation metadata for most (all?) DOIs.
 */
public class DoiResolver {
  private static final String CSL_TYPE = "application/vnd.citationstyles.csl+json";
  private Client client;

  public Citation resolve(DOI doi) {
    var c = new Citation();
    c.setId(doi.getDoiName());
    c.setDoi(doi);
    return c;
  }
}

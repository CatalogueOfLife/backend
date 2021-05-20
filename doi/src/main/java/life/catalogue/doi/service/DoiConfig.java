package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;

/**
 * DataCite DOI configuration.
 */
public class DoiConfig {

  public String api = "https://api.test.datacite.org";

  public String username;

  public String password;

  /**
   * DOI prefix to be used for COL DOIs.
   * Defaults to the test system.
   */
  public String prefix = "10.80631";


  public DOI datasetDOI(int datasetKey) {
    return DOI.dataset(prefix, datasetKey);
  }

  public DOI datasetSourceDOI(int datasetKey, int sourceKey) {
    return DOI.datasetSource(prefix, datasetKey, sourceKey);
  }

}

package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;
import life.catalogue.common.id.IdConverter;

/**
 * DataCite DOI configuration.
 */
public class DoiConfig {
  private static final String DATASET_PATH = "ds";
  private static final String DOWNLOAD_PATH = "dl";

  public String api = "https://api.test.datacite.org";

  public String username;

  public String password;

  /**
   * DOI prefix to be used for COL DOIs.
   * Defaults to the test system.
   */
  public String prefix = "10.80631";


  public DOI datasetDOI(int datasetKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey);
    return new DOI(prefix, suffix);
  }

  public DOI datasetSourceDOI(int datasetKey, int sourceKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey) + "-" + IdConverter.LATIN29.encode(sourceKey);
    return new DOI(prefix, suffix);
  }

}

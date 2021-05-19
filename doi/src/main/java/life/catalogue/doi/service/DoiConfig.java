package life.catalogue.doi.service;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;
import life.catalogue.common.id.IdConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DataCite DOI configuration.
 */
public class DoiConfig {
  private static final String DATASET_PATH = "d";
  private static final String EXPORT_PATH = "e";
  private static final String OTHER_PATH = "x";
  private static final Pattern DATASET_PATTERN = Pattern.compile("^"+DATASET_PATH+"([^-]+)$");
  private static final Pattern SOURCE_DATASET_PATTERN = Pattern.compile("^"+DATASET_PATH+"(.+)-(.+)$");

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

  public static int datasetKey(DOI doi) throws IllegalArgumentException {
    Preconditions.checkArgument(doi.isCOL(), "COL DOI required");
    Matcher m = DATASET_PATTERN.matcher(doi.getSuffix());
    if (m.find()) {
      return IdConverter.LATIN29.decode(m.group(1));
    }
    throw new IllegalArgumentException("Not a valid COL dataset DOI: " + doi);
  }

  public static DSID<Integer> sourceDatasetKey(DOI doi) throws IllegalArgumentException {
    Preconditions.checkArgument(doi.isCOL(), "COL DOI required");
    Matcher m = SOURCE_DATASET_PATTERN.matcher(doi.getSuffix());
    if (m.find()) {
      return DSID.of(IdConverter.LATIN29.decode(m.group(1)), IdConverter.LATIN29.decode(m.group(2)));
    }
    throw new IllegalArgumentException("Not a valid COL source dataset DOI: " + doi);
  }
}

package org.col.parser;

import org.col.api.vocab.License;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class LicenseParser extends EnumParser<License> {
  private static final Logger LOG = LoggerFactory.getLogger(LicenseParser.class);
  public static final LicenseParser PARSER = new LicenseParser();

  public LicenseParser() {
    super("license.csv", License.class);
    for (License r : License.values()) {
      add(r.title, r);
      add(r.url, r);
    }
  }

  public static License fromGbif(org.gbif.api.vocabulary.License gbif) {
    try {
      return gbif == null ? License.UNSPECIFIED : PARSER.parse(gbif.name()).get();

    } catch (Exception e) {
      LOG.warn("Unknown GBIF license {}", gbif);
      return License.UNSUPPORTED;
    }
  }

}

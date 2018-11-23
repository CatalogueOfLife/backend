package org.col.parser;

import org.col.api.vocab.License;

/**
 *
 */
public class LicenseParser extends EnumParser<License> {
  public static final LicenseParser PARSER = new LicenseParser();

  public LicenseParser() {
    super("license.csv", License.class);
    for (License r : License.values()) {
      add(r.title, r);
      add(r.url, r);
    }
  }

}

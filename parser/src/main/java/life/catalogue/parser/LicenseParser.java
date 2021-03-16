package life.catalogue.parser;

import life.catalogue.api.vocab.License;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 *
 */
public class LicenseParser extends EnumParser<License> {
  private static final Pattern CC0 = Pattern.compile("/publicdomain", Pattern.CASE_INSENSITIVE);
  private static final Pattern CCBY = Pattern.compile("/licenses/by/", Pattern.CASE_INSENSITIVE);
  private static final Pattern CCBYNC = Pattern.compile("/licenses/by-nc/", Pattern.CASE_INSENSITIVE);

  public static final LicenseParser PARSER = new LicenseParser();

  public LicenseParser() {
    super("license.csv", License.class);
    for (License r : License.values()) {
      add(r.title, r);
      add(r.url, r);
    }
  }

  @Override
  public Optional<License> parse(String value) throws UnparsableException {
    if (value != null) {
      if (CC0.matcher(value).find()) {
        return Optional.of(License.CC0);

      } else if (CCBYNC.matcher(value).find()) {
        return Optional.of(License.CC_BY_NC);

      } else if (CCBY.matcher(value).find()) {
        return Optional.of(License.CC_BY);
      }
    }
    return super.parse(value);
  }
}

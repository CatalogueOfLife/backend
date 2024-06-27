package life.catalogue.matching.util;

import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.NameParser;

/**
 * Utility class to provide a shared NameParser instance.
 */
public class NameParsers {
  public static final NameParser INSTANCE = new NameParserGBIF(20000);

  private NameParsers() {};
}

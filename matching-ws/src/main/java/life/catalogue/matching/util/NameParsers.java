package life.catalogue.matching.util;

import org.gbif.nameparser.NameParserGBIF;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to provide a shared NameParser instance.
 */
@Slf4j
public class NameParsers {
  public static final NameParserGBIF INSTANCE = new NameParserGBIF(20000);

  private NameParsers() {};
}

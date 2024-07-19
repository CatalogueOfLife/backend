package life.catalogue.matching.util;

import lombok.extern.slf4j.Slf4j;

import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.NameParser;

import java.io.IOException;

/**
 * Utility class to provide a shared NameParser instance.
 */
@Slf4j
public class NameParsers {
  public static final NameParserGBIF INSTANCE = new NameParserGBIF(20000);
  static {
    try {
      INSTANCE.configs().loadFromCLB();
    } catch (Exception e) {
      log.error("Failed to load name parser configs from CLB", e);
    }
  }

  private NameParsers() {};
}

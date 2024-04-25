package life.catalogue.matching;

import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.NameParser;

public class NameParsers {
  public static final NameParser INSTANCE = new NameParserGBIF(20000);

  private NameParsers() {};
}

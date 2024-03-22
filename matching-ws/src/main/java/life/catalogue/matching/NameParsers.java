package life.catalogue.matching;

import org.gbif.nameparser.NameParserGBIF;
public class NameParsers {
  public static final NameParserGBIF INSTANCE = new NameParserGBIF(20000);

  private NameParsers() {};
}

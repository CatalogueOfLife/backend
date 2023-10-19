package life.catalogue.parser;

import org.gbif.nameparser.api.NamePart;

/**
 *
 */
public class NamePartParser extends EnumParser<NamePart> {
  public static final NamePartParser PARSER = new NamePartParser();

  public NamePartParser() {
    super("namepart.csv", NamePart.class);
  }

}

package life.catalogue.parser;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;

/**
 *
 */
public class NomCodeParser extends EnumParser<NomCode> {
  public static final NomCodeParser PARSER = new NomCodeParser();

  public static boolean isCodeCompliant(NameType type) {
    return type==NameType.SCIENTIFIC || type==NameType.HYBRID_FORMULA || type==NameType.VIRUS;
  }

  public NomCodeParser() {
    super("nomcode.csv", NomCode.class);
    for (NomCode nc : NomCode.values()) {
      add(nc.getAbbrev(), nc);
      add(nc.getAcronym(), nc);
      add(nc.getTitle(), nc);
    }
  }

}

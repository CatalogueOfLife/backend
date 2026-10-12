package life.catalogue.parser;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;

/**
 *
 */
public class NomCodeParser extends EnumParser<NomCode> {
  public static final NomCodeParser PARSER = new NomCodeParser();

  public static boolean isCodeCompliant(NameType type) {
    // name-parser v4.2 dropped NameType.VIRUS. ICTV names parse as SCIENTIFIC, so code compliant by type is no
    // statement about the code: callers applying Linnean rules have to exclude NomCode.VIRUS themselves
    return type==NameType.SCIENTIFIC || type==NameType.FORMULA;
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

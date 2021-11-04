package life.catalogue.parser;

import de.undercouch.citeproc.csl.CSLType;

/**
 *
 */
public class CSLTypeParser extends EnumParser<CSLType> {
  public static final CSLTypeParser PARSER = new CSLTypeParser();

  public CSLTypeParser() {
    super("csltype.csv", CSLType.class);
    for (var t : CSLType.values()) {
      add(t.toString(), t);
    }
  }

}

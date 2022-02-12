package life.catalogue.parser;

import de.undercouch.citeproc.csl.CSLType;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public class CSLTypeParser extends EnumParser<CSLType> {
  public static final CSLTypeParser PARSER = new CSLTypeParser();

  public CSLTypeParser() {
    super("csltype.csv", CSLType.class);
    for (var t : CSLType.values()) {
      add(t.toString(), t);
      // also add inverted name parts, e.g. journal-article and article-journal
      // these are often found in the wild incl. crossref
      for (String delim : List.of("_", "-")) {
        if (t.toString().contains(delim)) {
          String[] parts = t.toString().split(delim);
          ArrayUtils.reverse(parts);
          String revName = String.join(delim, parts);
          add(revName, t);
        }
      }
    }
  }

}

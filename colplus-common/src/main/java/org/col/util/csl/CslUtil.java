package org.col.util.csl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import de.undercouch.citeproc.helper.json.StringJsonBuilder;
import de.undercouch.citeproc.helper.json.StringJsonBuilderFactory;

public class CslUtil {

  private static final Pattern PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

//  public static String createCslJson(String id, String author, String title, String year) {
//    CSLItemDataBuilder b = new CSLItemDataBuilder().id(id).author(new CSLNameBuilder().literal(author).build());
//  }
  
  public static String createCslJsonFromAcef(TermRecord rec) {
    CSLItemDataBuilder b = new CSLItemDataBuilder().id(rec.get(AcefTerm.ReferenceID))
        .author(new CSLNameBuilder().literal(rec.get(AcefTerm.Author)).build())
        .title(rec.get(AcefTerm.Title));
    Integer i = getYear(rec);
    if (i != null) {
      b.issued(i);
    }
    return (String) b.build().toJson(new StringJsonBuilder(new StringJsonBuilderFactory()));
  }

  private static Integer getYear(TermRecord rec) {
    String s = rec.get(AcefTerm.Year);
    if (s == null) {
      return null;
    }
    Matcher matcher = PATTERN.matcher(s);
    if (matcher.find()) {
      s = matcher.group(2);
      try {
        return Integer.valueOf(s);
      } catch (NumberFormatException e) {
      }
    }
    return null;
  }

}

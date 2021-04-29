package life.catalogue.common.csl;

import de.undercouch.citeproc.csl.CSLItemData;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;

public class CslUtil {
  private final static CslFormatter apa = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);

  /**
   * WARNING!
   * This is a very slow method that takes a second or more to build the citation string !!!
   * It uses the JavaScript citeproc library internally.
   */
  public static String buildCitation(Reference r) {
    return buildCitation(r.getCsl());
  }
  
  public static String buildCitation(CslData data) {
    if (data != null) {
      return apa.cite(data);
    }
    return null;
  }

  public static String buildCitation(CSLItemData data) {
    if (data != null) {
      return apa.cite(data);
    }
    return null;
  }

  
  
  
  
}

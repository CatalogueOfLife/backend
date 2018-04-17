package org.col.dw.reference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.col.api.model.CslItemData;
import org.col.api.model.Reference;
import org.col.dw.anystyle.AnystyleParserWrapper;
import com.google.common.base.Strings;

public class ReferenceFactory {

  private static final Pattern PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

  private final AnystyleParserWrapper anystyle;

  public ReferenceFactory() {
    anystyle = AnystyleParserWrapper.getInstance();
  }

  public Reference fromACEF(AcefReference acef) {
    Reference ref = new Reference();
    ref.setId(acef.getReferenceID());
    ref.setTitle(ref.getTitle());
    ref.setYear(getYear(acef.getYear()));
    /*
     * TODO This might not be correct. Might have to check "source" attribute instead (or as well).
     */
    ref.setCitation(acef.getDetails());
    CslItemData csl = anystyle.parse(acef.getDetails());
    ref.setCsl(csl);
    return ref;
  }

  public Reference fromDWC(DwcReference dwc) {
    Reference ref = new Reference();
    ref.setId(dwc.getIdentifier());
    ref.setTitle(dwc.getTitle());
    ref.setYear(getYear(dwc.getYear()));
    ref.setCitation(dwc.getBibliographicCitation());
    CslItemData csl = anystyle.parse(dwc.getBibliographicCitation());
    ref.setCsl(csl);
    return ref;

  }

  private static Integer getYear(String yearString) {
    if (Strings.isNullOrEmpty(yearString)) {
      return null;
    }
    try {
      return Integer.valueOf(yearString);
    } catch (NumberFormatException e) {
      Matcher matcher = PATTERN.matcher(yearString);
      if (matcher.find()) {
        String filtered = matcher.group(2);
        return Integer.valueOf(filtered);
      }
      return null;
    }
  }

}

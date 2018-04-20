package org.col.dw.reference;

import com.google.common.base.Strings;
import org.col.api.model.CslItemData;
import org.col.api.model.Reference;
import org.col.dw.anystyle.AnystyleParserWrapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReferenceFactory {

  private static final Pattern PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

  private final AnystyleParserWrapper anystyle;

  public ReferenceFactory() {
    anystyle = AnystyleParserWrapper.getInstance();
  }

  public Reference fromACEF(AcefReference acef) {
    Reference ref = new Reference();
    ref.setId(acef.getReferenceID());
    ref.getCsl().setTitle(acef.getTitle());
    ref.setYear(getYear(acef.getYear()));
    /*
     * TODO This might not be correct. Might have to check "source" attribute instead (or as well).
     */
    CslItemData csl = anystyle.parse(acef.getDetails());
    ref.setCsl(csl);
    return ref;
  }

  public Reference fromDWC(DwcReference dwc) {
    Reference ref = new Reference();
    ref.setId(dwc.getIdentifier());
    ref.getCsl().setTitle(dwc.getTitle());
    ref.setYear(getYear(dwc.getYear()));
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

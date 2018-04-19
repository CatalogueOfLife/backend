package org.col.dw.reference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.col.api.model.CslItemData;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.vocab.Issue;
import org.col.dw.anystyle.AnystyleParserWrapper;

public class ReferenceFactory {

  private static final Pattern PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

  private final Dataset dataset;
  private final AnystyleParserWrapper anystyle;

  public ReferenceFactory(Dataset dataset) {
    this.dataset = dataset;
    this.anystyle = AnystyleParserWrapper.getInstance();
  }

  public Reference fromACEF(AcefReference acef) {
    Reference ref = new Reference();
    ref.setDatasetKey(dataset.getKey());
    ref.setId(acef.getReferenceID());
    ref.setTitle(ref.getTitle());
    ref.setYear(getYear(acef.getYear()));
    /*
     * TODO Might have to check "source" attribute instead (or as well).
     */
    ref.setCitation(acef.getDetails());
    CslItemData csl = anystyle.parse(acef.getDetails());
    ref.setCsl(csl);
    checkCsl(ref);
    return ref;
  }

  public Reference fromDWC(DwcReference dwc) {
    Reference ref = new Reference();
    ref.setDatasetKey(dataset.getKey());
    ref.setId(dwc.getIdentifier());
    ref.setTitle(dwc.getTitle());
    ref.setYear(getYear(dwc.getYear()));
    ref.setCitation(dwc.getBibliographicCitation());
    CslItemData csl = anystyle.parse(dwc.getBibliographicCitation());
    ref.setCsl(csl);
    checkCsl(ref);
    return ref;
  }

  public Reference fromDC(DcReference dc) {
    Reference ref = new Reference();
    ref.setDatasetKey(dataset.getKey());
    ref.setId(dc.getIdentifier());
    ref.setTitle(dc.getTitle());
    ref.setYear(getYearFromDateString(dc.getDate()));
    ref.setCitation(dc.getBibliographicCitation());
    CslItemData csl = anystyle.parse(dc.getBibliographicCitation());
    ref.setCsl(csl);
    checkCsl(ref);
    return ref;
  }

  private static Integer getYearFromDateString(String date) {
    // TODO Use DateParser
    return null;
  }

  private static void checkCsl(Reference ref) {
    CslItemData csl = ref.getCsl();
    if (ref.getTitle() == null) {
      ref.setTitle(csl.getTitle());
    } else if (csl.getTitle() != null) {
      if (!ref.getTitle().equals(csl.getTitle())) {
        ref.addIssue(Issue.CSL_TITLE_MISMATCH);
      }
    }
    Integer cslYear = null;
    if (csl.getIssued().getDateParts() != null) {
      if (csl.getIssued().getDateParts()[0] != null) {
        if (csl.getIssued().getDateParts()[0][0] != 0) {
          cslYear = Integer.valueOf(csl.getIssued().getDateParts()[0][0]);
        }
      }
    } else {
      cslYear = getYear(csl.getYearSuffix());
    }
    if (ref.getYear() == null) {
      ref.setYear(cslYear);
    } else if (cslYear != null) {
      if (ref.getYear().intValue() != cslYear.intValue()) {
        ref.addIssue(Issue.CSL_YEAR_MISMATCH);
      }
    }
  }

  private static Integer getYear(String yearString) {
    if (yearString == null) {
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

package org.col.dw.reference;

import static com.google.common.base.Strings.emptyToNull;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;

public final class DwcReference {

  public static DwcReference fromTermRecord(TermRecord rec) {
    return new DwcReference(rec);
  }

  private DwcReference(TermRecord rec) {
    identifier = emptyToNull(rec.get(DcTerm.identifier));
    title = emptyToNull(rec.get(DcTerm.title));
    year = emptyToNull(rec.get(DwcTerm.year));
    bibliographicCitation = emptyToNull(rec.get(DcTerm.bibliographicCitation));
  }

  private final String identifier;
  private final String title;
  private final String year;
  private final String bibliographicCitation;
  // TODO more reference-related DWC terms

  public String getIdentifier() {
    return identifier;
  }

  public String getTitle() {
    return title;
  }

  public String getYear() {
    return year;
  }

  public String getBibliographicCitation() {
    return bibliographicCitation;
  }

}

package org.col.dw.reference;

import static com.google.common.base.Strings.emptyToNull;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.DcTerm;

public final class DcReference {

  public static DcReference fromTermRecord(TermRecord rec) {
    return new DcReference(rec);
  }

  private final String identifier;
  private final String bibliographicCitation;
  private final String title;
  private final String creator;
  private final String date;
  private final String source;

  private DcReference(TermRecord rec) {
    identifier = emptyToNull(rec.get(DcTerm.identifier));
    bibliographicCitation = emptyToNull(rec.get(DcTerm.bibliographicCitation));
    title = emptyToNull(rec.get(DcTerm.title));
    creator = emptyToNull(rec.get(DcTerm.creator));
    date = emptyToNull(rec.get(DcTerm.date));
    source = emptyToNull(rec.get(DcTerm.source));
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getBibliographicCitation() {
    return bibliographicCitation;
  }

  public String getTitle() {
    return title;
  }

  public String getCreator() {
    return creator;
  }

  public String getDate() {
    return date;
  }

  public String getSource() {
    return source;
  }

}

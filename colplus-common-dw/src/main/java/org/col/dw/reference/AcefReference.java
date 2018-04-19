package org.col.dw.reference;

import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import static com.google.common.base.Strings.emptyToNull;

public final class AcefReference {

  public static AcefReference fromTermRecord(TermRecord rec) {
    return new AcefReference(rec);
  }

  private AcefReference(TermRecord rec) {
    referenceID = emptyToNull(rec.get(AcefTerm.ReferenceID));
    authors = emptyToNull(rec.get(AcefTerm.Author));
    title = emptyToNull(rec.get(AcefTerm.Title));
    year = emptyToNull(rec.get(AcefTerm.Year));
    source = emptyToNull(rec.get(AcefTerm.Source));
    referenceType = emptyToNull(rec.get(AcefTerm.ReferenceType));
    details = emptyToNull(rec.get(AcefTerm.Details));
  }

  private final String referenceID;
  private final String authors;
  private final String title;
  private final String year;
  private final String source;
  private final String referenceType;
  private final String details;

  public String getReferenceID() {
    return referenceID;
  }

  public String getAuthors() {
    return authors;
  }

  public String getTitle() {
    return title;
  }

  public String getYear() {
    return year;
  }

  public String getSource() {
    return source;
  }

  public String getReferenceType() {
    return referenceType;
  }

  public String getDetails() {
    return details;
  }

}

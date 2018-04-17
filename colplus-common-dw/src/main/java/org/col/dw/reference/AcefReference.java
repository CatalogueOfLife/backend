package org.col.dw.reference;

import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;

public final class AcefReference {

  public static AcefReference fromTermRecord(TermRecord rec) {
    return new AcefReference(rec);
  }

  private AcefReference(TermRecord rec) {
    referenceID = rec.get(AcefTerm.ReferenceID);
    authors = rec.get(AcefTerm.Author);
    title = rec.get(AcefTerm.Title);
    year = rec.get(AcefTerm.Year);
    source = rec.get(AcefTerm.Source);
    details = rec.get(AcefTerm.Details);
  }

  private final String referenceID;
  private final String authors;
  private final String title;
  private final String year;
  private final String source;
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

  public String getDetails() {
    return details;
  }

}

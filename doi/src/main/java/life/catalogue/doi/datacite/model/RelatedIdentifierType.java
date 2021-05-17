package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum RelatedIdentifierType implements EnumValue {

  ARK("ARK"),
  AR_XIV("arXiv"),
  BIBCODE("bibcode"),
  DOI("DOI"),
  EAN_13("EAN13"),
  EISSN("EISSN"),
  HANDLE("Handle"),
  IGSN("IGSN"),
  ISBN("ISBN"),
  ISSN("ISSN"),
  ISTC("ISTC"),
  LISSN("LISSN"),
  LSID("LSID"),
  PMID("PMID"),
  PURL("PURL"),
  UPC("UPC"),
  URL("URL"),
  URN("URN"),
  W_3_ID("w3id");

  private final String value;

  RelatedIdentifierType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static RelatedIdentifierType fromValue(String v) {
    for (RelatedIdentifierType c : RelatedIdentifierType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}

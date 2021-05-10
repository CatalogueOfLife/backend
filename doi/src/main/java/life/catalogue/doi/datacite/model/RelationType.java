package life.catalogue.doi.datacite.model;

public enum RelationType {

  IS_CITED_BY("IsCitedBy"),
  CITES("Cites"),
  IS_SUPPLEMENT_TO("IsSupplementTo"),
  IS_SUPPLEMENTED_BY("IsSupplementedBy"),
  IS_CONTINUED_BY("IsContinuedBy"),
  CONTINUES("Continues"),
  IS_NEW_VERSION_OF("IsNewVersionOf"),
  IS_PREVIOUS_VERSION_OF("IsPreviousVersionOf"),
  IS_PART_OF("IsPartOf"),
  HAS_PART("HasPart"),
  IS_PUBLISHED_IN("IsPublishedIn"),
  IS_REFERENCED_BY("IsReferencedBy"),
  REFERENCES("References"),
  IS_DOCUMENTED_BY("IsDocumentedBy"),
  DOCUMENTS("Documents"),
  IS_COMPILED_BY("IsCompiledBy"),
  COMPILES("Compiles"),
  IS_VARIANT_FORM_OF("IsVariantFormOf"),
  IS_ORIGINAL_FORM_OF("IsOriginalFormOf"),
  IS_IDENTICAL_TO("IsIdenticalTo"),
  HAS_METADATA("HasMetadata"),
  IS_METADATA_FOR("IsMetadataFor"),
  REVIEWS("Reviews"),
  IS_REVIEWED_BY("IsReviewedBy"),
  IS_DERIVED_FROM("IsDerivedFrom"),
  IS_SOURCE_OF("IsSourceOf"),
  DESCRIBES("Describes"),
  IS_DESCRIBED_BY("IsDescribedBy"),
  HAS_VERSION("HasVersion"),
  IS_VERSION_OF("IsVersionOf"),
  REQUIRES("Requires"),
  IS_REQUIRED_BY("IsRequiredBy"),
  OBSOLETES("Obsoletes"),
  IS_OBSOLETED_BY("IsObsoletedBy");

  private final String value;

  RelationType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static RelationType fromValue(String v) {
    for (RelationType c : RelationType.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}

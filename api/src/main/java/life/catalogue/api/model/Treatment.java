package life.catalogue.api.model;

import life.catalogue.api.vocab.TreatmentFormat;

import java.util.Objects;

/**
 * A treatment, one per taxon only.
 * The ID is the taxonID.
 */
public class Treatment extends DatasetScopedEntity<String> implements VerbatimEntity {

  private Integer verbatimKey;
  private TreatmentFormat format;
  private String document;

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }

  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public TreatmentFormat getFormat() {
    return format;
  }

  public void setFormat(TreatmentFormat format) {
    this.format = format;
  }

  public String getDocument() {
    return document;
  }

  public void setDocument(String document) {
    this.document = document;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Treatment)) return false;
    if (!super.equals(o)) return false;
    Treatment treatment = (Treatment) o;
    return Objects.equals(verbatimKey, treatment.verbatimKey) &&
      format == treatment.format &&
      Objects.equals(document, treatment.document);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, format, document);
  }
}

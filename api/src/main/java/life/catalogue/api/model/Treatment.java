package life.catalogue.api.model;

import life.catalogue.api.vocab.TreatmentFormat;

/**
 * A treatment, one per taxon only.
 * The ID is the taxonID.
 */
public class Treatment extends DatasetScopedEntity<String> implements Referenced {

  private String referenceId;
  private TreatmentFormat format;
  private String document;

  @Override
  public String getReferenceId() {
    return referenceId;
  }

  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
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

}

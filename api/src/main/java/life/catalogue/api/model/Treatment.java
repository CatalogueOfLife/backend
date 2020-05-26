package life.catalogue.api.model;

import life.catalogue.api.vocab.TreatmentFormat;

public class Treatment implements DSID<String>, Referenced {
  private String id;
  private Integer datasetKey;
  private String referenceId;
  private TreatmentFormat format;
  private String document;

  /**
   * The taxonID. One treatment per taxon only.
   */
  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }

  @Override
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

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

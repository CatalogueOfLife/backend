package org.col.api.vocab;

public enum Catalogue {
  COL(Datasets.COL),
  PCAT(Datasets.PCAT);
  
  private final int datasetKey;
  
  Catalogue(int datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public int getDatasetKey() {
    return datasetKey;
  }
}

package org.col.api.vocab;

public enum Catalogue {
  COL(Datasets.SCRUT_CAT),
  PCAT(Datasets.PROV_CAT);
  
  private final int datasetKey;
  
  Catalogue(int datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public int getDatasetKey() {
    return datasetKey;
  }
}

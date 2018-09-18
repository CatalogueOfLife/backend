package org.col.api.vocab;

public enum Catalogue {
  SCRUTINIZED(Datasets.SCRUT_CAT),
  PROVISIONAL(Datasets.PROV_CAT);

  private final int datasetKey;

  Catalogue(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public int getDatasetKey() {
    return datasetKey;
  }
}

package org.col.es.ddl;

public class IndexSettings {

  private IndexAnalysis analysis;
  private IndexTuning index;

  public IndexAnalysis getAnalysis() {
    return analysis;
  }

  public void setAnalysis(IndexAnalysis analysis) {
    this.analysis = analysis;
  }

  public IndexTuning getIndex() {
    return index;
  }

  public void setIndex(IndexTuning index) {
    this.index = index;
  }

}

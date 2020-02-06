package life.catalogue.es.ddl;

/**
 * The "settings" object within an index definitions. Defines analyzers and basic index configuration.
 */
public class Settings {

  private Analysis analysis;
  private Index index;

  /**
   * Returns the analyzers, tokenizers, etc to be used by the index
   * 
   * @return
   */
  public Analysis getAnalysis() {
    if (analysis == null) {
      analysis = new Analysis();
    }
    return analysis;
  }

  public void setAnalysis(Analysis analysis) {
    this.analysis = analysis;
  }

  /**
   * Returns other index-configuration settings.
   * 
   * @return
   */
  public Index getIndex() {
    if (index == null) {
      index = new Index();
    }
    return index;
  }

  public void setIndex(Index index) {
    this.index = index;
  }

}

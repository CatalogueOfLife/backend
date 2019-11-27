package life.catalogue.es.ddl;

import java.io.IOException;
import java.io.InputStream;

import life.catalogue.es.EsModule;
import life.catalogue.es.EsUtil;

/**
 * The "settings" object within an index definitions. Consists of an "analysis" object defining tokenizers and
 * analyzers, and an "index" object for tuning the index.
 *
 */
public class Settings {

  public static Settings getDefaultSettings() throws IOException {
    InputStream is = EsUtil.class.getResourceAsStream("es-settings.json");
    return EsModule.readDDLObject(is, Settings.class);
  }

  private Analysis analysis;
  private Index index;

  public Analysis getAnalysis() {
    return analysis;
  }

  public void setAnalysis(Analysis analysis) {
    this.analysis = analysis;
  }

  public Index getIndex() {
    return index;
  }

  public void setIndex(Index index) {
    this.index = index;
  }

}

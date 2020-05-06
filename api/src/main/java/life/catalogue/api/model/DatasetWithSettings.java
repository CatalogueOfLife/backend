package life.catalogue.importer.coldp;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Setting;
import org.gbif.nameparser.api.NomCode;

public class ColdpDataset extends Dataset {
  private final DatasetSettings settings = new DatasetSettings();

  public DatasetSettings getSettings() {
    return settings;
  }

  public void setCode(NomCode code){
    settings.put(Setting.NOMENCLATURAL_CODE, code);
  }

  public void setGazetteer(Gazetteer gazetteer){
    settings.put(Setting.DISTRIBUTION_GAZETTEER, gazetteer);
  }

  public void setDataFormat(DataFormat format){
    settings.put(Setting.DATA_FORMAT, format);
  }

}

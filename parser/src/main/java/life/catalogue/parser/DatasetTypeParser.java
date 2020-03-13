package life.catalogue.parser;

import life.catalogue.api.vocab.DatasetType;

/**
 *
 */
public class DatasetTypeParser extends EnumParser<DatasetType> {
  public static final DatasetTypeParser PARSER = new DatasetTypeParser();

  public DatasetTypeParser() {
    super("datasettype.csv", DatasetType.class);
  }

}

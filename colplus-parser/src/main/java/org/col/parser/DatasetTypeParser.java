package org.col.parser;

import org.col.api.vocab.DatasetType;

/**
 *
 */
public class DatasetTypeParser extends EnumParser<DatasetType> {
  public static final DatasetTypeParser PARSER = new DatasetTypeParser();

  public DatasetTypeParser() {
    super("datasettype.csv", DatasetType.class);
  }

}

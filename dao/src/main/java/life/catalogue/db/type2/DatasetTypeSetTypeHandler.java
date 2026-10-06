package life.catalogue.db.type2;

import life.catalogue.api.vocab.DatasetType;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

public class DatasetTypeSetTypeHandler extends BaseEnumSetTypeHandler<DatasetType> {

  public DatasetTypeSetTypeHandler() {
    super(DatasetType.class, true);
  }
}

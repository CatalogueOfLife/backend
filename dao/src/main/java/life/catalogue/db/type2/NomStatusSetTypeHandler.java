package life.catalogue.db.type2;

import life.catalogue.api.vocab.NomStatus;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

public class NomStatusSetTypeHandler extends BaseEnumSetTypeHandler<NomStatus> {

  public NomStatusSetTypeHandler() {
    super(NomStatus.class, false);
  }

}

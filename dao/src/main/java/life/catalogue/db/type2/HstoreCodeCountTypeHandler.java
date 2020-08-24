package life.catalogue.db.type2;

import org.gbif.nameparser.api.NomCode;

public class HstoreCodeCountTypeHandler extends HstoreEnumCountTypeHandlerBase<NomCode> {

  public HstoreCodeCountTypeHandler() {
    super(NomCode.class);
  }
}

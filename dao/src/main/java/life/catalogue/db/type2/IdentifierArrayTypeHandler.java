package life.catalogue.db.type2;

import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.SimpleName;
import life.catalogue.common.text.CSVUtils;

import org.apache.commons.lang3.StringUtils;
import org.postgresql.util.PGobject;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PG Array type handler that supports reads & writes for lists of identifier objects into a pg text array.
 */
public class IdentifierArrayTypeHandler extends AbstractArrayTypeHandler<List<Identifier>> {

  public IdentifierArrayTypeHandler() {
    super("text", null);
  }

  @Override
  public Object[] toArray(List<Identifier> obj) throws SQLException {
    List<String> result = new ArrayList<>();
    for (Identifier id : obj) {
      result.add(id.toString());
    }
    return result.toArray();
  }

  @Override
  public List<Identifier> toObj(Array pgArray) throws SQLException {
    List<Identifier> ids = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        String val = o.toString();
        if (!StringUtils.isBlank(val)) {
          Identifier id = Identifier.parse(val);
          ids.add(id);
        }
      }
    }
    return ids.isEmpty() ? null : ids;
  }

}

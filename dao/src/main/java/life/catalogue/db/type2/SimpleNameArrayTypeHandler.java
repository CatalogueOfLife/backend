package life.catalogue.db.type2;

import life.catalogue.api.model.SimpleName;
import life.catalogue.common.text.CSVUtils;
import org.postgresql.util.PGobject;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complex simple_name PG Array type handler that only supports reads, but not writes.
 */
public class SimpleNameArrayTypeHandler extends AbstractArrayTypeHandler<List<SimpleName>> {

  public SimpleNameArrayTypeHandler() {
    super("simple_name", Collections.emptyList());
  }

  @Override
  public Object[] toArray(List<SimpleName> obj) throws SQLException {
    List<PGobject> result = new ArrayList<>();
    for (SimpleName sn : obj) {
      var cols = SimpleNameTypeHandler.cols(sn);
      result.add(CustomAbstractTypeHandler.buildPgObject(arrayType, cols));
    }
    return result.toArray(Object[]::new);
  }

  @Override
  public List<SimpleName> toObj(Array pgArray) throws SQLException {
    List<SimpleName> cl = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        String row = o.toString();
        List<String> cols = CSVUtils.parseLine(row.substring(1, row.length()-1));
        cl.add(SimpleNameTypeHandler.from(cols));
      }
    }
    return cl;
  }

}

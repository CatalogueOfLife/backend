package life.catalogue.db.type2;

import life.catalogue.api.model.Duplicate;
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
 * A read only PG Array type handler that parses id, sectorKey tuples into a list of Duplicate.Homonym instances.
 */
public class HomonymArrayTypeHandler extends AbstractArrayTypeHandler<List<Duplicate.Homonym>> {

  public HomonymArrayTypeHandler() {
    super("text", Collections.emptyList());
  }

  @Override
  public Object[] toArray(List<Duplicate.Homonym> obj) throws SQLException {
    throw new SQLException("This type handler is read only");
  }

  @Override
  public List<Duplicate.Homonym> toObj(Array pgArray) throws SQLException {
    List<Duplicate.Homonym> cl = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        String row = o.toString();
        List<String> cols = CSVUtils.parseLine(row.substring(1, row.length()-1));
        cl.add(new Duplicate.Homonym(cols.get(0), parseInt(cols.get(1))));
      }
    }
    return cl;
  }

  private static Integer parseInt(String x) {
    return StringUtils.isBlank(x) ? null : Integer.parseInt(x);
  }

}

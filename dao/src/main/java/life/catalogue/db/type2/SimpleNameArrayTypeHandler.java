package life.catalogue.db.type2;

import life.catalogue.api.model.SimpleName;
import life.catalogue.common.text.CSVUtils;
import org.apache.ibatis.type.TypeException;
import org.gbif.nameparser.api.Rank;

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
    throw new RuntimeException("writes not supported");
  }

  @Override
  public List<SimpleName> toObj(Array pgArray) throws SQLException {
    List<SimpleName> cl = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        // (k6,KINGDOM,Plantae)
        String row = o.toString();
        List<String> cols = CSVUtils.parseLine(row.substring(1, row.length()-1));
        if (cols.size() == 3) {
          Rank rank = cols.get(1) == null ? null : Rank.valueOf(cols.get(1));
          cl.add(new SimpleName(cols.get(0), cols.get(2), rank));
        } else {
          // how can that be ?
          throw new TypeException("Failed to parse "+o+" to SimpleName");
        }
      }
    }
    return cl;
  }

}

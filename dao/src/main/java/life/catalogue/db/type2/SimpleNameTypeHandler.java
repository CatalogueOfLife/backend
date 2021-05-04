package life.catalogue.db.type2;

import life.catalogue.api.model.SimpleName;
import life.catalogue.db.type2.CustomAbstractTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;
import org.gbif.nameparser.api.Rank;

import java.sql.SQLException;
import java.util.List;

/**
 * Complex simple_name type handler that supports reads & writes.
 */
public class SimpleNameTypeHandler extends CustomAbstractTypeHandler<SimpleName> {

  public SimpleNameTypeHandler() {
    super("simple_name");
  }

  @Override
  public String[] toAttributes(SimpleName obj) throws SQLException {
    return cols(obj);
  }

  @Override
  public SimpleName fromAttributes(List<String> cols) throws SQLException {
    return from(cols);
  }

  public static String[] cols(SimpleName obj) throws SQLException {
    String rank = obj.getRank() == null ? null : obj.getRank().name();
    return new String[]{obj.getId(), rank, obj.getName(), obj.getAuthorship()};
  }

  public static SimpleName from(List<String> cols) throws TypeException {
    // (k6,KINGDOM,Plantae,null)
    if (cols.size() == 4) {
      Rank rank = cols.get(1) == null ? null : Rank.valueOf(cols.get(1));
      return new SimpleName(cols.get(0), cols.get(2), cols.get(3), rank);
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse "+String.join(",", cols)+" to SimpleName");
    }
  }

}

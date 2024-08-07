package life.catalogue.db.type2;

import life.catalogue.common.text.CSVUtils;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

/**
 * A generic mybatis type handler that translates an object to a postgres custom type.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public abstract class CustomAbstractTypeHandler<T> extends BaseTypeHandler<T> {

  private final String typeName;

  public CustomAbstractTypeHandler(String typeName) {
    this.typeName = typeName;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    String[] cols = toAttributes(parameter);
    ps.setObject(i, buildPgObject(typeName, cols));
  }

  static PGobject buildPgObject(String typeName, String[] cols) throws SQLException {
    PGobject pgObject = new PGobject();
    pgObject.setType(typeName);
    String value = Arrays.stream(cols)
      .map(CustomAbstractTypeHandler::pgEscape)
      .collect(Collectors.joining(","));
    // (k6,KINGDOM,,"Murmica maximus")
    pgObject.setValue("(" + value + ")");
    return pgObject;
  }

  /**
   * Quotes all values to not worry about commas.
   * Escapes quotes by doubling them.
   * NULLs become empty strings.
   */
  static String pgEscape(String x) {
    return x == null ? "" : '"' + x.replaceAll("\"", "\"\"") + '"';
  }

  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromObject(rs.getObject(columnName));
  }
  
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromObject(rs.getObject(columnIndex));
  }
  
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromObject(cs.getObject(columnIndex));
  }

  T fromObject(Object obj) throws SQLException {
    if (obj == null) return null;
    return fromAttributes(toCols(obj));
  }

  static List<String> toCols(Object obj) throws SQLException {
    if (obj==null) return null;
    // (k6,KINGDOM,,"Murmica maximus")
    String row = obj.toString();
    return CSVUtils.parseLine(row.substring(1, row.length()-1));
  }

  public abstract String[] toAttributes(T obj) throws SQLException;

  public abstract T fromAttributes(List<String> cols) throws SQLException;

}

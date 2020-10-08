package life.catalogue.db.type2;

import life.catalogue.common.text.CSVUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.List;

/**
 * A generic mybatis type handler that translates an object to a postgres custom type.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public abstract class CustomTypeAbstractHandler<T> extends BaseTypeHandler<T> {

  private final String typeName;

  public CustomTypeAbstractHandler(String typeName) {
    this.typeName = typeName;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    String[] cols = toRow(parameter);
    ps.setObject(i, build(typeName, cols));
  }

  static PGobject build(String typeName, String[] cols) throws SQLException {
    // (k6,KINGDOM,"Murmica maximus")
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    boolean first = true;
    for (String col : cols){
      if (first) {
        first=false;
      } else {
        sb.append(",");
      }
      if (col != null) {
        sb.append("\"");
        sb.append(col.replaceAll("\"", "\"\""));
        sb.append("\"");
      }
    }
    sb.append(")");
    PGobject pgObject = new PGobject();
    pgObject.setType(typeName);
    pgObject.setValue(sb.toString());
    return pgObject;
  }

  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromRow(rs.getObject(columnName));
  }
  
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromRow(rs.getObject(columnIndex));
  }
  
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromRow(cs.getObject(columnIndex));
  }

  T fromRow(Object obj) throws SQLException {
    if (obj == null) return null;
    return fromRow(toCols(obj));
  }

  static List<String> toCols(Object obj) throws SQLException {
    if (obj==null) return null;
    // (k6,KINGDOM,Plantae)
    // (Hans,Peter,,)
    String row = obj.toString();
    return CSVUtils.parseLine(row.substring(1, row.length()-1));
  }

  public abstract String[] toRow(T obj) throws SQLException;

  public abstract T fromRow(List<String> cols) throws SQLException;

}

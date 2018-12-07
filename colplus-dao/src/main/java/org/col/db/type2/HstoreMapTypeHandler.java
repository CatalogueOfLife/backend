package org.col.db.type2;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.HStoreConverter;

/**
 * A mybatis base type handler that translates from the generic java.util.Map<String, String> to the
 * postgres hstore database type.
 */
public class HstoreMapTypeHandler extends BaseTypeHandler<Map<String, String>> {
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<String, String> parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, HStoreConverter.toString(parameter));
  }
  
  @Override
  public Map<String, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }
  
  @Override
  public Map<String, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }
  
  @Override
  public Map<String, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }
  
  private Map<String, String> fromString(String hstring) {
    return hstring == null ? new HashMap<>() : HStoreConverter.fromString(hstring);
  }
  
}

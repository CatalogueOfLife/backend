package org.col.db.type2;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.col.api.jackson.ApiModule;

/**
 * A generic mybatis type handler that translates an object to the postgres jsonb database type
 * by using Jackson JSON (de)serialisation.
 */
@MappedJdbcTypes(JdbcType.OTHER)
abstract class JsonAbstractHandler<T> extends BaseTypeHandler<T> {
  
  final String typeName;
  
  public JsonAbstractHandler(String typeName) {
    this.typeName = typeName;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter,
                                  JdbcType jdbcType) throws SQLException {
    try {
      String x = ApiModule.MAPPER.writeValueAsString(parameter);
      ps.setString(i, x);
    } catch (JsonProcessingException e) {
      throw new SQLException("Unable to convert " + typeName + " to JSONB", e);
    }
  }
  
  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }
  
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }
  
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }
  
  abstract T fromString(String jsonb) throws SQLException;
  
}

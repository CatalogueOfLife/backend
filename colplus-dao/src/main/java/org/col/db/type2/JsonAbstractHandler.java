package org.col.db.type2;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.col.api.jackson.ApiModule;

/**
 * A generic mybatis type handler that translates an object to the postgres jsonb database type
 * by using Jackson JSON (de)serialisation.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonAbstractHandler<T> extends BaseTypeHandler<T> {
  
  private final Class<T> clazz;
  
  public JsonAbstractHandler(Class<T> clazz) {
    this.clazz = clazz;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter,
                                  JdbcType jdbcType) throws SQLException {
    try {
      String x = ApiModule.MAPPER.writeValueAsString(parameter);
      ps.setString(i, x);
    } catch (JsonProcessingException e) {
      throw new SQLException("Unable to convert " + clazz.getSimpleName() + " to JSONB", e);
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
  

  private T fromString(String jsonb) throws SQLException {
    if (!Strings.isNullOrEmpty(jsonb)) {
      try {
        return ApiModule.MAPPER.readValue(jsonb, clazz);
      } catch (IOException e) {
        throw new SQLException("Unable to convert JSONB to " + clazz.getSimpleName(), e);
      }
    }
    return null;
  }
}

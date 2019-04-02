package org.col.db.type2;

import java.io.IOException;
import java.sql.SQLException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.col.api.jackson.ApiModule;

/**
 * A generic mybatis type handler that translates an object to the postgres jsonb database type
 * by using Jackson JSON (de)serialisation.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class Json2TypeRefHandler<T> extends JsonAbstractHandler<T> {
  
  private final TypeReference<T> type;
  
  public Json2TypeRefHandler(String typeName, TypeReference<T> type) {
    super(typeName);
    this.type = type;
  }
  
  @Override
  T fromString(String jsonb) throws SQLException {
    if (!Strings.isNullOrEmpty(jsonb)) {
      try {
        return ApiModule.MAPPER.readValue(jsonb, type);
      } catch (IOException e) {
        throw new SQLException("Unable to convert JSONB to " + typeName, e);
      }
    }
    return null;
  }
  
}

package org.col.db.type2;

import java.io.IOException;
import java.sql.SQLException;

import com.google.common.base.Strings;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.col.api.jackson.ApiModule;

/**
 * A generic mybatis type handler that translates an object to the postgres jsonb database type
 * by using Jackson JSON (de)serialisation.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class Json2ClassHandler<T> extends JsonAbstractHandler<T> {
  
  private final Class<T> clazz;
  
  public Json2ClassHandler(Class<T> clazz) {
    super(clazz.getSimpleName());
    this.clazz = clazz;
  }
  
  @Override
  T fromString(String jsonb) throws SQLException {
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

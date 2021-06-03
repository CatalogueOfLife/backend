package life.catalogue.db.type2;

import life.catalogue.api.jackson.ApiModule;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;

/**
 * A generic mybatis type handler that translates an object to the postgres jsonb database type
 * by using Jackson JSON (de)serialisation.
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonAbstractHandler<T> extends BaseTypeHandler<T> {

  private final ObjectReader reader;
  private final ObjectWriter writer;
  private final String typeName;

  public JsonAbstractHandler(String typeName, TypeReference<T> typeRef) {
    this.typeName = typeName;
    // object readers & writers are slightly more performant than simple object mappers
    // they also are thread safe!
    reader = ApiModule.MAPPER.readerFor(typeRef);
    writer = ApiModule.MAPPER.writerFor(typeRef);
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter,
                                  JdbcType jdbcType) throws SQLException {
    try {
      String x = writer.writeValueAsString(parameter);
      ps.setString(i, x);
    } catch (JsonProcessingException e) {
      throw new SQLException("Unable to convert " + typeName + " to JSONB", e);
    }
  }
  
  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromJson(rs.getString(columnName));
  }
  
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromJson(rs.getString(columnIndex));
  }
  
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromJson(cs.getString(columnIndex));
  }
  

  protected T fromJson(String json) throws SQLException {
    if (!Strings.isNullOrEmpty(json)) {
      try {
        return reader.readValue(json);
      } catch (IOException e) {
        throw new SQLException("Unable to convert JSONB to " + typeName, e);
      }
    }
    return null;
  }

}

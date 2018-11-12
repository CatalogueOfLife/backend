package org.col.db.type2;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.col.api.jackson.ApiModule;
import org.gbif.dwc.terms.Term;

/**
 * Postgres type handler converting an map of term values into a postgres JSONB data type.
 */
public class TermMapTypeHandler extends BaseTypeHandler<Map<Term, String>> {
  
  private static final TypeReference<Map<Term, String>> TERM_MAP_TYPE = new TypeReference<Map<Term, String>>() {
  };
  private final ObjectReader reader;
  private final ObjectWriter writer;
  
  
  public TermMapTypeHandler() {
    // object readers & writers are slightly more performant than simple object mappers
    // they also are thread safe!
    reader = ApiModule.MAPPER.readerFor(TERM_MAP_TYPE);
    writer = ApiModule.MAPPER.writerFor(TERM_MAP_TYPE);
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<Term, String> parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, toJson(parameter));
  }
  
  @Override
  public Map<Term, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromJson(rs.getString(columnName));
  }
  
  @Override
  public Map<Term, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromJson(rs.getString(columnIndex));
  }
  
  @Override
  public Map<Term, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromJson(cs.getString(columnIndex));
  }
  
  private Map<Term, String> fromJson(String json) throws SQLException {
    if (json != null) {
      try {
        return reader.readValue(json);
      } catch (IOException e) {
        throw new SQLException("Cannot deserialize term map from JSON", e);
      }
    }
    return null;
  }
  
  private String toJson(Map<Term, String> tr) throws SQLException {
    if (tr != null) {
      try {
        return writer.writeValueAsString(tr);
        
      } catch (IOException e) {
        throw new SQLException("Cannot serialize term map into JSON", e);
      }
    }
    return null;
  }
  
}

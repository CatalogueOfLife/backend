package org.col.db.type;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.col.api.jackson.ApiModule;
import org.col.api.model.ExtendedTermRecord;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Postgres type handler converting an entire TermRecord instance into a postgres JSONB data type.
 */
public class ExtendedTermRecordTypeHandler extends BaseTypeHandler<ExtendedTermRecord> {

  private final ObjectReader reader;
  private final ObjectWriter writer;


  public ExtendedTermRecordTypeHandler() {
    // object readers & writers are slightly more performant than simple object mappers
    // they also are thread safe!
    reader = ApiModule.MAPPER.readerFor(ExtendedTermRecord.class);
    writer = ApiModule.MAPPER.writerWithView(ExtendedTermRecord.class);
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, ExtendedTermRecord parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, toJson(parameter));
  }

  @Override
  public ExtendedTermRecord getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromJson(rs.getString(columnName));
  }

  @Override
  public ExtendedTermRecord getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromJson(rs.getString(columnIndex));
  }

  @Override
  public ExtendedTermRecord getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromJson(cs.getString(columnIndex));
  }

  private ExtendedTermRecord fromJson(String json) throws SQLException {
    if (json != null) {
      try {
        return reader.readValue(json);
      } catch (IOException e) {
        throw new SQLException("Cannot deserialize TermRecord from JSON", e);
      }
    }
    return null;
  }

  private String toJson(ExtendedTermRecord tr) throws SQLException {
    if (tr != null) {
      try {
        return writer.writeValueAsString(tr);

      } catch (IOException e) {
        throw new SQLException("Cannot serialize TermRecord into JSON", e);
      }
    }
    return null;
  }

}

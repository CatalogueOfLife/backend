package org.col.db.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.col.api.VerbatimRecordTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Postgres type handler converting an entire VerbatimRecordTerms instance into a postgres JSONB data type.
 */
public class VerbatimTypeHandler extends BaseTypeHandler<VerbatimRecordTerms> {

  private static final Logger LOG = LoggerFactory.getLogger(VerbatimTypeHandler.class);
  private final ObjectReader reader;
  private final ObjectWriter writer;


  public VerbatimTypeHandler() {
    ObjectMapper mapper = new ObjectMapper();
    // object readers & writers are slightly more performant than simple object mappers
    // they also are thread safe!
    reader = mapper.readerFor(VerbatimRecordTerms.class);
    writer = mapper.writerWithView(VerbatimRecordTerms.class);
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, VerbatimRecordTerms parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, toJson(parameter));
  }

  @Override
  public VerbatimRecordTerms getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromJson(rs.getString(columnName));
  }

  @Override
  public VerbatimRecordTerms getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromJson(rs.getString(columnIndex));
  }

  @Override
  public VerbatimRecordTerms getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromJson(cs.getString(columnIndex));
  }

  private VerbatimRecordTerms fromJson(String json) {
    if (json != null) {
      try {
        return reader.readValue(json);
      } catch (IOException e) {
        LOG.error("Cannot deserialize VerbatimRecordTerms from json", e);
      }
    }
    return null;
  }

  private String toJson(VerbatimRecordTerms verbatim) {
    if (verbatim != null) {
      try {
        return writer.writeValueAsString(verbatim);

      } catch (IOException e) {
        LOG.error("Cannot serialize VerbatimRecordTerms into json", e);
      }
    }
    return null;
  }

}

package org.col.dw.db.type;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A mybatis type handler that translates from the typed java.util.ObjectNode to the
 * postgres hstore database type. Any non enum values in hstore are silently ignored.
 *
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml.
 */
@MappedTypes(ObjectNode.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class CslJsonHandler extends BaseTypeHandler<ObjectNode> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, ObjectNode parameter, JdbcType jdbcType) throws SQLException {
    try {
      ps.setString(i, MAPPER.writeValueAsString(parameter));
    } catch (JsonProcessingException e) {
      throw new SQLException("Unable to convert CSL to JSON", e);
    }
  }

  @Override
  public ObjectNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }

  @Override
  public ObjectNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }

  @Override
  public ObjectNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }

  private ObjectNode fromString(String jsonb) throws SQLException {
    if (!Strings.isNullOrEmpty(jsonb)) {
      try {
        return (ObjectNode) MAPPER.readTree(jsonb);
      } catch (IOException e) {
        throw new SQLException("Unable to convert JSONB to ObjectNode", e);
      }
    }
    return null;
  }

}

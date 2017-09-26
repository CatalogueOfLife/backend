package org.col.db.type;

import com.google.common.base.Strings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.col.api.vocab.Issue;
import org.postgresql.util.HStoreConverter;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

/**
 * A mybatis type handler that translates from the typed java.util.Map<Issue, String> to the
 * postgres hstore database type. Any non enum values in hstore are silently ignored.
 *
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml.
 */
@MappedTypes(EnumMap.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class HstoreIssueHandler extends BaseTypeHandler<EnumMap<Issue, String>> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, EnumMap<Issue, String> parameter, JdbcType jdbcType)
    throws SQLException {
    ps.setString(i, HStoreConverter.toString(parameter));
  }

  @Override
  public EnumMap<Issue, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }

  @Override
  public EnumMap<Issue, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }

  @Override
  public EnumMap<Issue, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }

  private EnumMap<Issue, String> fromString(String hstring) {
    EnumMap<Issue, String> typedMap = new EnumMap(Issue.class);
    if (!Strings.isNullOrEmpty(hstring)) {
      Map<String, String> rawMap = HStoreConverter.fromString(hstring);
      for (Map.Entry<String, String> entry : rawMap.entrySet()) {
        try {
          typedMap.put(Issue.valueOf(entry.getKey()), entry.getValue());
        } catch (IllegalArgumentException e) {
          // ignore this entry
        }
      }
    }
    return typedMap;
  }


}

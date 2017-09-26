package org.col.db.type;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.col.api.vocab.Issue;
import org.postgresql.util.HStoreConverter;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * A mybatis type handler that translates from the typed java.util.Map<Issue, String> to the
 * postgres hstore database type. Any non enum values in hstore are silently ignored.
 *
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml.
 */
public class HstoreIssueHandler extends BaseTypeHandler<Map<Issue, String>> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<Issue, String> parameter, JdbcType jdbcType)
    throws SQLException {
    ps.setString(i, HStoreConverter.toString(parameter));
  }

  @Override
  public Map<Issue, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }

  @Override
  public Map<Issue, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }

  @Override
  public Map<Issue, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }

  private Map<Issue, String> fromString(String hstring) {
    HashMap<Issue, String> typedMap = Maps.newHashMap();
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

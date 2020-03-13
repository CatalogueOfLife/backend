package life.catalogue.db.type2;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.postgresql.util.HStoreConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mybatis base type handler that translates from the generic java.util.Map<Enum, Integer> to the
 * postgres hstore database type. Any non integer values in hstore are silently ignored.
 * All enumerations are serialized by their name, not ordinal.
 * <p>
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml, for example see DatasetImportMapper.xml.
 */
public class HstoreTermCountTypeHandler extends BaseTypeHandler<Map<Term, Integer>> {
  private static final Logger LOG = LoggerFactory.getLogger(HstoreTermCountTypeHandler.class);
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<Term, Integer> parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, HStoreConverter.toString(parameter));
  }
  
  @Override
  public Map<Term, Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }
  
  @Override
  public Map<Term, Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }
  
  @Override
  public Map<Term, Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }
  
  private Map<Term, Integer> fromString(String hstring) {
    HashMap<Term, Integer> typedMap = Maps.newHashMap();
    if (!Strings.isNullOrEmpty(hstring)) {
      Map<String, String> rawMap = HStoreConverter.fromString(hstring);
      for (Map.Entry<String, String> entry : rawMap.entrySet()) {
        try {
          
          typedMap.put(TermFactory.instance().findTerm(entry.getKey(), true), Integer.parseInt(entry.getValue()));
        } catch (IllegalArgumentException e) {
          // ignore this entry
          LOG.warn("Illegal count map value found in hstore: {}", entry);
        }
      }
    }
    return typedMap;
  }
  
}

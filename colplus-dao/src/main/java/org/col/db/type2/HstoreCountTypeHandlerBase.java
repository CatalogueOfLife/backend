package org.col.db.type2;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.HStoreConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mybatis base type handler that translates from the generic java.util.Map<Enum, Integer> to the
 * postgres hstore database type. Any non integer values in hstore are silently ignored.
 * All enumerations are serialized by their name, not ordinal.
 *
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml, for example see DatasetImportMapper.xml.
 */
public abstract class HstoreCountTypeHandlerBase<KEY extends Enum> extends BaseTypeHandler<Map<KEY, Integer>> {
  private static final Logger LOG = LoggerFactory.getLogger(HstoreCountTypeHandlerBase.class);

  private final Class<KEY> enumClass;
  private final KEY[] values;

  public HstoreCountTypeHandlerBase(Class<KEY> enumClass) {
    this.enumClass = enumClass;
    values = enumClass.getEnumConstants();
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<KEY, Integer> parameter, JdbcType jdbcType)
    throws SQLException {
    ps.setString(i, HStoreConverter.toString(parameter));
  }

  @Override
  public Map<KEY, Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return fromString(rs.getString(columnName));
  }

  @Override
  public Map<KEY, Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return fromString(rs.getString(columnIndex));
  }

  @Override
  public Map<KEY, Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return fromString(cs.getString(columnIndex));
  }

  private Map<KEY, Integer> fromString(String hstring) {
    HashMap<KEY, Integer> typedMap = Maps.newHashMap();
    if (!Strings.isNullOrEmpty(hstring)) {
      Map<String, String> rawMap = HStoreConverter.fromString(hstring);
      for (Map.Entry<String, String> entry : rawMap.entrySet()) {
        try {
          int val = Integer.parseInt(entry.getValue());
          if (val > 0) {
            if (!Strings.isNullOrEmpty(entry.getKey())) {
              typedMap.put(toKey(entry.getKey()), val);
            }
          }
        } catch (IllegalArgumentException e) {
          // ignore this entry
          LOG.warn("Illegal enum {} value found in hstore: {}", enumClass.getSimpleName(), entry.getKey());
        }
      }
      if (typedMap.size() != rawMap.size()) {
        LOG.error("BAD TYPE!!!");
      }
    }
    Map<KEY, Integer> sorted = sortMap(typedMap);
    if (typedMap.size() != sorted.size()) {
      LOG.error("BAD SORTED!!!");
    }
    return sorted;
  }

  /**
   * Can be overridden to return sorted maps in custom manners.
   * By default sorts the map according to the count values in descending order.
   */
  protected Map<KEY, Integer> sortMap(HashMap<KEY, Integer> map) {
    return sortMapByValuesDesc(map);
  }

  @VisibleForTesting
  protected static <KEY extends Comparable> Map<KEY, Integer> sortMapByValuesDesc(HashMap<KEY, Integer> map) {
    final Ordering<KEY> reverseValuesAndNaturalKeysOrdering =
        Ordering.natural()
            .reverse()
            .nullsLast()
            .onResultOf(Functions.forMap(map, null)) // natural for values
            .compound(Ordering.natural()); // secondary - natural ordering of keys
    return ImmutableSortedMap.copyOf(map, reverseValuesAndNaturalKeysOrdering);
  }

  private KEY toKey(String key) throws IllegalArgumentException {
    try {
      return (KEY) Enum.valueOf(enumClass, key);
    } catch (IllegalArgumentException e) {
      // in case we just generated new metrics we do not yet have the final enum names
      // but the simple enum conversion whatever that is for the specific type
      return toKeyAlt(key);
    }
  }

  /**
   * Default that tries to resolve via ordinals.
   * Override in case the enum is stored in other forms for simple values, e.g. 2 char country codes
   */
  KEY toKeyAlt(String key) throws IllegalArgumentException {
    Integer ord = Integer.parseInt(key);
    return values[ord];
  }

}

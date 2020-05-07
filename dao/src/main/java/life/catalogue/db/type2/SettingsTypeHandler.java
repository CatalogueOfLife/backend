package life.catalogue.db.type2;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.jackson.SettingsDeserializer;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.api.vocab.Setting;
import org.apache.ibatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Postgres type handler converting an map of object values into a postgres JSONB data type.
 */
public class SettingsTypeHandler extends JsonAbstractHandler<Map<Setting, Object>> {

  public SettingsTypeHandler() {
    super("map", new TypeReference<Map<Setting, Object>>() {});
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Map<Setting, Object> parameter, JdbcType jdbcType) throws SQLException {
    // we treat frequency special and store its days to allow simpler calculations in SQL
    if (parameter.containsKey(Setting.IMPORT_FREQUENCY)) {
      Map<Setting, Object> freqMap = new HashMap<>(parameter);
      Frequency freq = (Frequency) freqMap.get(Setting.IMPORT_FREQUENCY);
      freqMap.replace(Setting.IMPORT_FREQUENCY, freq.getDays());
      super.setNonNullParameter(ps, i, freqMap, jdbcType);
    } else {
      super.setNonNullParameter(ps, i, parameter, jdbcType);
    }
  }

  @Override
  protected Map<Setting, Object> fromJson(String json) throws SQLException {
    Map<Setting, Object> map = super.fromJson(json);
    if (map == null) return Collections.emptyMap();

    // we treat frequency special and store its days to allow simpler calculations in SQL
    Integer days = (Integer) map.remove(Setting.IMPORT_FREQUENCY);
    SettingsDeserializer.convertFromJSON(map);
    if (days != null) {
      map.put(Setting.IMPORT_FREQUENCY, Frequency.fromDays(days));
    }
    return map;
  }


}

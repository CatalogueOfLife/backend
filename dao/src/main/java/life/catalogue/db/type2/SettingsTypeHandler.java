package life.catalogue.db.type2;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SettingsDeserializer;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.api.vocab.Setting;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;

/**
 * Postgres type handler converting an map of object values into a postgres JSONB data type.
 */
public class SettingsTypeHandler extends JsonAbstractHandler<Map<Setting, Object>> {
  private static final ObjectReader RAW_READER = ApiModule.MAPPER.readerFor(new TypeReference<Map<String, Object>>() {});

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
    if (Strings.isNullOrEmpty(json)) return Collections.emptyMap();
    Map<Setting, Object> map;
    try {
      // skips keys that are no setting (anymore) instead of failing every reader of the dataset
      map = SettingsDeserializer.keysFromJson(RAW_READER.readValue(json));
    } catch (IOException e) {
      throw new SQLException("Unable to convert JSONB to dataset settings", e);
    }
    // we treat frequency special and store its days to allow simpler calculations in SQL
    Integer days = (Integer) map.remove(Setting.IMPORT_FREQUENCY);
    SettingsDeserializer.convertFromJSON(map);
    if (days != null) {
      map.put(Setting.IMPORT_FREQUENCY, Frequency.fromDays(days));
    }
    return map;
  }


}

package life.catalogue.db.type2;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.DatasetSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Postgres type handler converting an map of object values into a postgres JSONB data type.
 */
public class SettingsTypeHandler extends JsonAbstractHandler<Map<DatasetSettings, Object>> {
  private static final Logger LOG = LoggerFactory.getLogger(SettingsTypeHandler.class);

  public SettingsTypeHandler() {
    super("map", new TypeReference<Map<DatasetSettings, Object>>() {});
  }

  @Override
  protected Map<DatasetSettings, Object> fromJson(String json) throws SQLException {
    Map<DatasetSettings, Object> map = super.fromJson(json);
    if (map != null) {
      for (Map.Entry<DatasetSettings, Object> e : map.entrySet()) {
        DatasetSettings s = e.getKey();
        try {
          if (s.getType().equals(LocalDate.class)) {
            map.replace(e.getKey(), LocalDate.parse((String) e.getValue()));
          } else if (s.isEnum()) {
            map.replace(e.getKey(), VocabularyUtils.lookupEnum((String) e.getValue(), (Class<Enum<?>>) s.getType()));
          }
        } catch (RuntimeException ex) {
          LOG.error("Unable to convert value {} for setting {} into {}", e.getValue(), e.getKey(), e.getKey().getType());
          map.remove(e.getKey());
        }
      }
    }
    return map;
  }
}

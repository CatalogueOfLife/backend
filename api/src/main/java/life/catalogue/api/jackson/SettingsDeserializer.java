package life.catalogue.api.jackson;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Setting;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class SettingsDeserializer extends JsonDeserializer {

  private static final Logger LOG = LoggerFactory.getLogger(SettingsDeserializer.class);
  private static final TypeReference<Map<String, Object>> RAW = new TypeReference<Map<String, Object>>() {};

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    Map<Setting, Object> map = keysFromJson(p.readValueAs(RAW));
    convertFromJSON(map);
    return DatasetSettings.of(map);
  }

  /**
   * Looks up the setting of every key, skipping keys that are no setting (anymore) with a warning:
   * one stale key must not make the settings of a dataset unreadable. Values are left as they are.
   */
  public static Map<Setting, Object> keysFromJson(Map<String, Object> raw) {
    Map<Setting, Object> map = new HashMap<>();
    if (raw != null) {
      for (Map.Entry<String, Object> e : raw.entrySet()) {
        try {
          map.put(VocabularyUtils.lookupEnum(e.getKey(), Setting.class), e.getValue());
        } catch (IllegalArgumentException ex) {
          LOG.warn("Ignore unknown dataset setting {}", e.getKey());
        }
      }
    }
    return map;
  }

  public static void convertFromJSON(Map<Setting, Object> map){
    if (map != null) {
      for (Map.Entry<Setting, Object> e : map.entrySet()) {
        if (e.getValue() == null) continue;
        Setting s = e.getKey();
        if (s.isMultiple()) {
          List<Object> converted = new ArrayList<>();
          for (Object val : (List) e.getValue()) {
            converted.add(readSingleValue(s, val));
          }
          map.replace(s, converted);
        } else {
          map.replace(s, readSingleValue(s, e.getValue()));
        }
      }
    }
  }

  private static Object readSingleValue(Setting key, Object value) {
    try {
      if (key.getType().equals(LocalDate.class)) {
        return LocalDate.parse((String) value);
      } else if (key.getType().equals(URI.class)) {
        return URI.create( (String) value);
      } else if (key.getType().equals(UUID.class)) {
        return UUID.fromString( (String) value);
      } else if (key.isEnum()) {
        return VocabularyUtils.lookupEnum((String) value, (Class<Enum<?>>) key.getType());
      } else {
        // String, Integer or Boolean are converted natively in JSON already
        return value;
      }
    } catch (RuntimeException ex) {
      LOG.error("Unable to convert value {} for setting {} into {}", value, key, key.getType());
      throw new IllegalArgumentException("Unable to convert value "+value+" for setting " + key + " into " + key.getType());
    }
  }
}

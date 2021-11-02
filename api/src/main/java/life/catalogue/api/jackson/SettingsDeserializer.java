package life.catalogue.api.jackson;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Setting;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class SettingsDeserializer extends JsonDeserializer {

  private static final Logger LOG = LoggerFactory.getLogger(SettingsDeserializer.class);
  private static final TypeReference<Map<Setting, Object>> REF = new TypeReference<Map<Setting, Object>>() {};

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    Map<Setting, Object> raw = p.readValueAs(REF);
    convertFromJSON(raw);
    return DatasetSettings.of(raw);
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

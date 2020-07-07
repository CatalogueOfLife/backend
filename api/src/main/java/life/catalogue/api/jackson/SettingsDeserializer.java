package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

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
        try {
          if (s.getType().equals(LocalDate.class)) {
            map.replace(e.getKey(), LocalDate.parse((String) e.getValue()));
          } else if (s.getType().equals(URI.class)) {
            map.replace(e.getKey(), URI.create( (String) e.getValue()));
          } else if (s.isEnum()) {
            map.replace(e.getKey(), VocabularyUtils.lookupEnum((String) e.getValue(), (Class<Enum<?>>) s.getType()));
          } else {
            // String, Integer or Boolean are converted natively in JSON already
          }
        } catch (RuntimeException ex) {
          LOG.error("Unable to convert value {} for setting {} into {}", e.getValue(), e.getKey(), e.getKey().getType());
          throw new IllegalArgumentException("Unable to convert value "+e.getValue()+" for setting " + e.getKey() + " into " + e.getKey().getType());
        }
      }
    }
  }
}

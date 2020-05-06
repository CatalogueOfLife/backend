package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * terms as used in the verbatim class.
 * It renders terms with their prefixed name or full term URI if unknown.
 */
public class SettingsSerde extends JsonDeserializer {
  TypeReference<Map<Setting, Object>> REF = new TypeReference<Map<Setting, Object>>() {};

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    Map<Setting, Object> map = p.readValueAs(REF);
    DatasetSettings ds = new DatasetSettings();
    for (Map.Entry<Setting, Object> entry : map.entrySet()) {
      final Class type = entry.getKey().getType();
      if (type.isEnum()) {
        ds.put(entry.getKey(), entry.getValue());
      } else if (type.equals(LocalDate.class)) {
        ds.put(entry.getKey(), entry.getValue());
      } else if (type.equals(URI.class)) {
        ds.put(entry.getKey(), entry.getValue());
      } else {
        // String, Integer or Boolean are converted natively in JSON
        ds.put(entry.getKey(), entry.getValue());
      }
    }
    return ds;
  }
}
